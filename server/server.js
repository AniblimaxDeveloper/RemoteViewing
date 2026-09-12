import { WebSocketServer } from 'ws';
import crypto from 'node:crypto';

const PORT = Number(process.env.PORT || 8080);
const wss = new WebSocketServer({ port: PORT });
const targets = new Map(); // deviceKey -> Map(installId -> ws)
const sessions = new Map(); // controller ws -> {target, deviceKey, token}

function send(ws, value) {
  if (ws && ws.readyState === ws.OPEN) ws.send(JSON.stringify(value));
}
function open(ws) { return ws && ws.readyState === ws.OPEN; }
function removeTarget(ws) {
  const k = ws.deviceKey, id = ws.installId;
  const bucket = targets.get(k);
  if (bucket) {
    if (bucket.get(id) === ws) bucket.delete(id);
    if (!bucket.size) targets.delete(k);
  }
}

wss.on('connection', ws => {
  ws.role = null;
  ws.deviceKey = null;
  ws.installId = null;

  ws.on('message', (raw, isBinary) => {
    if (isBinary) {
      if (ws.role === 'target') {
        const session = [...sessions.values()].find(s => s.target === ws);
        if (session && open(session.controller)) session.controller.send(raw, { binary: true });
      }
      return;
    }
    let msg; try { msg = JSON.parse(raw.toString()); } catch { return; }

    if (msg.type === 'REGISTER_TARGET') {
      const deviceKey = String(msg.deviceKey || '').trim();
      const installId = String(msg.installId || '').trim();
      const model = String(msg.model || deviceKey).trim();
      if (!deviceKey || !installId || deviceKey.length > 120 || installId.length > 120) {
        send(ws,{type:'ERROR',message:'Identitas perangkat tidak valid.'}); return;
      }
      let bucket = targets.get(deviceKey); if (!bucket) { bucket = new Map(); targets.set(deviceKey,bucket); }
      const old = bucket.get(installId); if (old && old !== ws && open(old)) old.close(1000,'replaced');
      ws.role='target'; ws.deviceKey=deviceKey; ws.installId=installId; ws.model=model; bucket.set(installId,ws);
      send(ws,{type:'REGISTERED',model}); return;
    }

    if (msg.type === 'REQUEST_SESSION') {
      const deviceKey = String(msg.deviceKey || '').trim();
      const bucket = targets.get(deviceKey);
      if (!bucket || !bucket.size) { send(ws,{type:'ERROR',message:'Perangkat dengan model tersebut offline.'}); return; }
      const target = [...bucket.values()].find(open) || null;
      if (!target) { send(ws,{type:'ERROR',message:'Perangkat offline.'}); return; }
      for (const s of sessions.values()) if (s.target === target) { send(ws,{type:'ERROR',message:'Perangkat sedang dipakai sesi lain.'}); return; }
      const token = crypto.randomUUID();
      ws.role='controller'; ws.deviceKey=deviceKey; sessions.set(ws,{controller:ws,target,deviceKey,token});
      send(target,{type:'ACCESS_REQUEST',controllerName:String(msg.controllerName||'Emotexware')});
      send(ws,{type:'WAITING_APPROVAL'}); return;
    }

    if (msg.type === 'APPROVE_SESSION' || msg.type === 'REJECT_SESSION') {
      const entry=[...sessions.values()].find(s=>s.target===ws);
      if (!entry) return;
      if (msg.type==='APPROVE_SESSION') send(entry.controller,{type:'SESSION_APPROVED'});
      else { send(entry.controller,{type:'SESSION_REJECTED'}); if(open(entry.controller)) entry.controller.close(1000,'rejected'); sessions.delete(entry.controller); }
      return;
    }

    if (msg.type === 'CONTROL' && ws.role === 'controller') {
      const entry=sessions.get(ws); if(entry && entry.controller===ws && open(entry.target)) send(entry.target,msg);
      return;
    }
  });

  ws.on('close',()=>{
    if(ws.role==='target'){
      const impacted=[...sessions.entries()].filter(([,s])=>s.target===ws);
      removeTarget(ws);
      for(const [controller] of impacted){ send(controller,{type:'TARGET_OFFLINE'}); if(open(controller)) controller.close(1000,'target offline'); sessions.delete(controller); }
    } else if(ws.role==='controller'){
      const entry=sessions.get(ws); if(entry){ send(entry.target,{type:'SESSION_ENDED'}); sessions.delete(ws); }
    }
  });
});

console.log(`Emotexware server listening on ws://0.0.0.0:${PORT}`);
