import { WebSocketServer } from 'ws';

const PORT = Number(process.env.PORT || 8080);
const wss = new WebSocketServer({ port: PORT });
const targets = new Map(); // code -> ws
const controllers = new Map(); // ws -> code
const sessions = new Map(); // code -> { target, controller }

function send(ws, value) {
  if (ws && ws.readyState === ws.OPEN) ws.send(JSON.stringify(value));
}

function closeSession(code) {
  const s = sessions.get(code);
  if (!s) return;
  send(s.target, { type: 'SESSION_ENDED' });
  send(s.controller, { type: 'SESSION_ENDED' });
  try { s.target.close(); } catch {}
  try { s.controller.close(); } catch {}
  targets.delete(code);
  controllers.delete(s.controller);
  sessions.delete(code);
}

wss.on('connection', (ws) => {
  ws.role = null;
  ws.code = null;

  ws.on('message', (raw, isBinary) => {
    if (isBinary) {
      if (ws.role === 'target' && ws.code) {
        const s = sessions.get(ws.code);
        if (s?.controller?.readyState === s.controller.OPEN) s.controller.send(raw, { binary: true });
      }
      return;
    }

    let msg;
    try { msg = JSON.parse(raw.toString()); } catch { return; }

    if (msg.type === 'REGISTER_TARGET') {
      const code = String(msg.code || '');
      if (!/^\d{6}$/.test(code) || targets.has(code)) {
        send(ws, { type: 'ERROR', message: 'Kode pairing tidak valid atau sudah dipakai.' });
        return;
      }
      ws.role = 'target';
      ws.code = code;
      targets.set(code, ws);
      send(ws, { type: 'REGISTERED', code });
      return;
    }

    if (msg.type === 'REQUEST_SESSION') {
      const code = String(msg.code || '');
      const target = targets.get(code);
      if (!target) {
        send(ws, { type: 'ERROR', message: 'Device ID tidak ditemukan / offline.' });
        return;
      }
      ws.role = 'controller';
      ws.code = code;
      controllers.set(ws, code);
      sessions.set(code, { target, controller: ws });
      send(target, { type: 'ACCESS_REQUEST', controllerName: String(msg.controllerName || 'Controller') });
      send(ws, { type: 'WAITING_APPROVAL' });
      return;
    }

    const code = ws.code;
    const s = code ? sessions.get(code) : null;

    if (msg.type === 'APPROVE_SESSION' && ws.role === 'target' && s?.target === ws) {
      send(s.controller, { type: 'SESSION_APPROVED' });
      return;
    }

    if (msg.type === 'REJECT_SESSION' && ws.role === 'target' && s?.target === ws) {
      send(s.controller, { type: 'SESSION_REJECTED' });
      sessions.delete(code);
      controllers.delete(s.controller);
      try { s.controller.close(); } catch {}
      return;
    }

    if (msg.type === 'CONTROL' && ws.role === 'controller' && s?.controller === ws) {
      send(s.target, msg);
      return;
    }
  });

  ws.on('close', () => {
    const code = ws.code;
    if (!code) return;
    const s = sessions.get(code);
    if (ws.role === 'target') {
      targets.delete(code);
      if (s) {
        send(s.controller, { type: 'TARGET_OFFLINE' });
        try { s.controller.close(); } catch {}
        controllers.delete(s.controller);
        sessions.delete(code);
      }
    } else if (ws.role === 'controller') {
      controllers.delete(ws);
      if (s?.target) send(s.target, { type: 'SESSION_ENDED' });
      sessions.delete(code);
    }
  });
});

console.log(`Remote Viewing server listening on ws://0.0.0.0:${PORT}`);
