import 'dotenv/config';
import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import rateLimit from 'express-rate-limit';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import fs from 'fs';
import { z } from 'zod';
import { randomUUID } from 'crypto';
import { availableModels, findModel, streamChat } from './providers.js';

if (!process.env.JWT_SECRET) { console.error('JWT_SECRET is required (see .env.example)'); process.exit(1); }
const SECRET = process.env.JWT_SECRET;

fs.mkdirSync('data', { recursive: true });
const FILE = 'data/users.json';
const load = () => (fs.existsSync(FILE) ? JSON.parse(fs.readFileSync(FILE, 'utf8')) : []);
const save = (u) => fs.writeFileSync(FILE, JSON.stringify(u));
// NOTE: JSON file storage is fine for a first release. Swap for Postgres before scaling.

const app = express();
app.set('trust proxy', 1);
app.use(helmet());
app.use(cors());
app.use(express.json({ limit: '1mb' }));
app.use(rateLimit({ windowMs: 60_000, limit: 120 }));
const authLimit = rateLimit({ windowMs: 15 * 60_000, limit: 20, message: { error: 'Too many attempts. Try again later.' } });
const chatLimit = rateLimit({ windowMs: 60_000, limit: 30, keyGenerator: (r) => r.user?.id || r.ip, message: { error: 'Rate limit reached. Slow down a little.' } });

const sign = (u) => jwt.sign({ id: u.id, guest: !!u.guest }, SECRET, { expiresIn: '30d' });
const pub = (u) => ({ id: u.id, name: u.name || '', email: u.email || '', guest: !!u.guest });
const bad = (res, msg, code = 400) => res.status(code).json({ error: msg });

function auth(req, res, next) {
  try { req.user = jwt.verify((req.headers.authorization || '').replace('Bearer ', ''), SECRET); next(); }
  catch { bad(res, 'Please sign in again.', 401); }
}

const creds = z.object({ email: z.string().email().max(200), password: z.string().min(8).max(200), name: z.string().max(80).optional() });

app.post('/auth/register', authLimit, async (req, res) => {
  const p = creds.safeParse(req.body);
  if (!p.success) return bad(res, 'Enter a valid email and a password of at least 8 characters.');
  const users = load();
  const email = p.data.email.toLowerCase();
  if (users.some((u) => u.email === email)) return bad(res, 'An account with this email already exists.', 409);
  const u = { id: randomUUID(), email, name: p.data.name || email.split('@')[0], hash: await bcrypt.hash(p.data.password, 12) };
  save([...users, u]);
  res.json({ token: sign(u), user: pub(u) });
});

app.post('/auth/login', authLimit, async (req, res) => {
  const p = creds.pick({ email: true, password: true }).safeParse(req.body);
  const u = p.success && load().find((x) => x.email === p.data.email.toLowerCase());
  if (!u || !(await bcrypt.compare(p.data.password, u.hash))) return bad(res, 'Incorrect email or password.', 401);
  res.json({ token: sign(u), user: pub(u) });
});

app.post('/auth/guest', authLimit, (req, res) => {
  const u = { id: randomUUID(), name: 'Guest', guest: true };
  res.json({ token: sign(u), user: pub(u) });
});

app.post('/auth/forgot', authLimit, (req, res) =>
  bad(res, 'Password reset email is not configured on this server yet.', 501));

app.delete('/account', auth, (req, res) => {
  save(load().filter((u) => u.id !== req.user.id));
  res.json({ ok: true });
});

app.get('/v1/models', auth, (req, res) => res.json(availableModels()));

const chatSchema = z.object({
  model: z.string(),
  system: z.string().max(8000).default(''),
  messages: z.array(z.object({ role: z.enum(['user', 'assistant']), content: z.string().min(1).max(30000) })).min(1).max(100),
});

app.post('/v1/chat', auth, chatLimit, async (req, res) => {
  const p = chatSchema.safeParse(req.body);
  if (!p.success) return bad(res, 'Invalid request.');
  const m = findModel(p.data.model);
  if (!m) return bad(res, 'That model is not configured on the server.', 404);
  res.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-cache', connection: 'keep-alive' });
  const ac = new AbortController();
  res.on('close', () => ac.abort());
  try {
    for await (const d of streamChat(m, p.data.system, p.data.messages, ac.signal)) res.write(`data: ${JSON.stringify({ d })}\n\n`);
    res.write('data: [DONE]\n\n');
  } catch (e) {
    if (!ac.signal.aborted) res.write(`data: ${JSON.stringify({ error: e.message || 'Stream failed.' })}\n\n`);
  }
  res.end();
});

app.post('/v1/search', auth, chatLimit, async (req, res) => {
  if (!process.env.TAVILY_API_KEY) return bad(res, 'Web search is not configured on this server.', 503);
  const q = z.object({ query: z.string().min(1).max(300) }).safeParse(req.body);
  if (!q.success) return bad(res, 'Invalid query.');
  const r = await fetch('https://api.tavily.com/search', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ api_key: process.env.TAVILY_API_KEY, query: q.data.query, max_results: 5 }),
  });
  if (!r.ok) return bad(res, 'Search provider error.', 502);
  const j = await r.json();
  res.json((j.results || []).map(({ title, url, content }) => ({ title, url, snippet: content })));
});

app.post('/v1/images', auth, chatLimit, async (req, res) => {
  if (!process.env.OPENAI_API_KEY) return bad(res, 'Image generation is not configured on this server.', 503);
  const q = z.object({ prompt: z.string().min(1).max(1000), style: z.string().max(40).optional() }).safeParse(req.body);
  if (!q.success) return bad(res, 'Invalid prompt.');
  const r = await fetch((process.env.OPENAI_BASE_URL || 'https://api.openai.com/v1') + '/images/generations', {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: `Bearer ${process.env.OPENAI_API_KEY}` },
    body: JSON.stringify({ model: 'gpt-image-1', prompt: q.data.style ? `${q.data.prompt}. Style: ${q.data.style}` : q.data.prompt, size: '1024x1024' }),
  });
  if (!r.ok) return bad(res, 'Image provider error.', 502);
  const j = await r.json();
  res.json({ b64: j.data?.[0]?.b64_json });
});

app.listen(process.env.PORT || 8787, () => console.log('AXO backend running'));
