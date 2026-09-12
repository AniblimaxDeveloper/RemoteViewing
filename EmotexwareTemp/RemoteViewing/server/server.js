import { WebSocketServer } from 'ws';

const PORT = Number(process.env.PORT || 8080);
const wss = new WebSocketServer({ port: PORT });

// Human-readable device lookup key -> currently connected target.
// This is an identifier only; it is NOT an authentication credential.
const targets = new Map();
const controllers = new Map(); // ws -> deviceKey
const sessions = new Map(); // deviceKey -> { target, controller }

function send(ws, value) {
  if (ws && ws.readyState === ws.OPEN) ws.send(JSON.stringify(value));
}

function closeSocket(ws) {
  try { ws.close(); } catch {}
}

wss.on('connection', (ws) => {
  ws.role = null;
  ws.deviceKey = null;

  ws.on('message', (raw, isBinary) => {
    if (isBinary) {
      if (ws.role === 'target' && ws.deviceKey) {
        const session = sessions.get(ws.deviceKey);
        if (session?.controller?.readyState === session.controller.OPEN) {
          session.controller.send(raw, { binary: true });
        }
      }
      return;
    }

    let msg;
    try { msg = JSON.parse(raw.toString()); } catch { return; }

    if (msg.type === 'REGISTER_TARGET') {
      const deviceKey = String(msg.deviceKey || '').trim();
      const model = String(msg.model || '').trim();

      if (!deviceKey || deviceKey.length > 120) {
        send(ws, { type: 'ERROR', message: 'Identitas perangkat tidak valid.' });
        return;
      }

      const previous = targets.get(deviceKey);
      if (previous && previous !== ws && previous.readyState === previous.OPEN) {
        send(ws, { type: 'ERROR', message: 'Perangkat dengan identitas tersebut sudah online.' });
        return;
      }

      ws.role = 'target';
      ws.deviceKey = deviceKey;
      ws.model = model;
      targets.set(deviceKey, ws);
      send(ws, { type: 'REGISTERED', deviceKey, model });
      return;
    }

    if (msg.type === 'REQUEST_SESSION') {
      const deviceKey = String(msg.deviceKey || '').trim();
      const target = targets.get(deviceKey);

      if (!target || target.readyState !== target.OPEN) {
        send(ws, { type: 'ERROR', message: 'Perangkat tidak ditemukan atau sedang offline.' });
        return;
      }

      const existing = sessions.get(deviceKey);
      if (existing) {
        send(ws, { type: 'ERROR', message: 'Perangkat sedang menjalankan sesi lain.' });
        return;
      }

      ws.role = 'controller';
      ws.deviceKey = deviceKey;
      controllers.set(ws, deviceKey);
      sessions.set(deviceKey, { target, controller: ws });

      send(target, {
        type: 'ACCESS_REQUEST',
        controllerName: String(msg.controllerName || 'Controller'),
        requestedModel: target.model || ''
      });
      send(ws, { type: 'WAITING_APPROVAL', model: target.model || '' });
      return;
    }

    const deviceKey = ws.deviceKey;
    const session = deviceKey ? sessions.get(deviceKey) : null;

    if (msg.type === 'APPROVE_SESSION' && ws.role === 'target' && session?.target === ws) {
      send(session.controller, { type: 'SESSION_APPROVED' });
      return;
    }

    if (msg.type === 'REJECT_SESSION' && ws.role === 'target' && session?.target === ws) {
      send(session.controller, { type: 'SESSION_REJECTED' });
      controllers.delete(session.controller);
      closeSocket(session.controller);
      sessions.delete(deviceKey);
      return;
    }

    if (msg.type === 'CONTROL' && ws.role === 'controller' && session?.controller === ws) {
      send(session.target, msg);
      return;
    }
  });

  ws.on('close', () => {
    const deviceKey = ws.deviceKey;
    if (!deviceKey) return;

    const session = sessions.get(deviceKey);

    if (ws.role === 'target') {
      if (targets.get(deviceKey) === ws) targets.delete(deviceKey);
      if (session) {
        send(session.controller, { type: 'TARGET_OFFLINE' });
        controllers.delete(session.controller);
        closeSocket(session.controller);
        sessions.delete(deviceKey);
      }
    } else if (ws.role === 'controller') {
      controllers.delete(ws);
      if (session?.target) send(session.target, { type: 'SESSION_ENDED' });
      sessions.delete(deviceKey);
    }
  });
});

console.log(`Emotexware server listening on ws://0.0.0.0:${PORT}`);
