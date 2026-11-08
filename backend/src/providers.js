import fs from 'fs';

// Edit models.json to add/remove models. No app rebuild needed.
const cfg = JSON.parse(fs.readFileSync(new URL('../models.json', import.meta.url)));
const hasKey = (m) => !!process.env[m.keyEnv];
export const availableModels = () => cfg.filter(hasKey).map(({ id, label, role }) => ({ id, label, role }));
export const findModel = (id) => cfg.find((m) => m.id === id && hasKey(m));

export async function* streamChat(m, system, messages, signal) {
  const key = process.env[m.keyEnv];
  if (m.provider === 'anthropic') {
    const r = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST', signal,
      headers: { 'content-type': 'application/json', 'x-api-key': key, 'anthropic-version': '2023-06-01' },
      body: JSON.stringify({ model: m.model, max_tokens: m.maxTokens || 4096, stream: true, system: system || undefined, messages }),
    });
    yield* sse(r, (j) => (j.type === 'content_block_delta' ? j.delta?.text : null));
  } else {
    const base = process.env.OPENAI_BASE_URL || 'https://api.openai.com/v1';
    const msgs = system ? [{ role: 'system', content: system }, ...messages] : messages;
    const r = await fetch(base + '/chat/completions', {
      method: 'POST', signal,
      headers: { 'content-type': 'application/json', authorization: `Bearer ${key}` },
      body: JSON.stringify({ model: m.model, stream: true, messages: msgs }),
    });
    yield* sse(r, (j) => j.choices?.[0]?.delta?.content);
  }
}

async function* sse(r, pick) {
    if (!r.ok) {
    const t = (await r.text()).slice(0, 500);
    console.error('Provider error', r.status, t);
    let m = '';
    try { m = JSON.parse(t).error?.message || ''; } catch {}
    throw new Error(`Provider error ${r.status}: ${m || t}`.slice(0, 300));
    }
  const dec = new TextDecoder();
  let buf = '';
  for await (const chunk of r.body) {
    buf += dec.decode(chunk, { stream: true });
    let i;
    while ((i = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, i).trim();
      buf = buf.slice(i + 1);
      if (!line.startsWith('data:')) continue;
      const d = line.slice(5).trim();
      if (d === '[DONE]') return;
      try { const t = pick(JSON.parse(d)); if (t) yield t; } catch { /* ignore keepalives */ }
    }
  }
}
