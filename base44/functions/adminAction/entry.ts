import { createClientFromRequest } from 'npm:@base44/sdk@0.8.25';

Deno.serve(async (req) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type' } });
    }

    try {
        const base44 = createClientFromRequest(req);
        const body = await req.json();
        const { admin_token, action, payload } = body;

        if (admin_token !== 'hushtv_admin_Masterpan1') {
            return Response.json({ error: 'Unauthorized' }, { status: 401 });
        }

        // Send broadcast message
        if (action === 'broadcast') {
            const { message, title, type, expires_at } = payload;
            const msg = await base44.asServiceRole.entities.BroadcastMessage.create({
                message,
                title: title || '',
                type: type || 'info',
                is_active: true,
                expires_at: expires_at || null,
                sent_by: 'admin'
            });
            return Response.json({ success: true, broadcast: msg });
        }

        // Deactivate broadcast
        if (action === 'deactivate_broadcast') {
            await base44.asServiceRole.entities.BroadcastMessage.update(payload.id, { is_active: false });
            return Response.json({ success: true });
        }

        // Delete broadcast
        if (action === 'delete_broadcast') {
            await base44.asServiceRole.entities.BroadcastMessage.delete(payload.id);
            return Response.json({ success: true });
        }

        // Set config value
        if (action === 'set_config') {
            const { key, value, label } = payload;
            const existing = await base44.asServiceRole.entities.AppConfig.filter({ key }, '-created_date', 1);
            if (existing.length > 0) {
                await base44.asServiceRole.entities.AppConfig.update(existing[0].id, { value, label: label || key });
            } else {
                await base44.asServiceRole.entities.AppConfig.create({ key, value, label: label || key });
            }
            return Response.json({ success: true });
        }

        // Delete config
        if (action === 'delete_config') {
            await base44.asServiceRole.entities.AppConfig.delete(payload.id);
            return Response.json({ success: true });
        }

        // Kick/delete session
        if (action === 'delete_session') {
            await base44.asServiceRole.entities.UserSession.delete(payload.id);
            return Response.json({ success: true });
        }

        // Clear all old sessions
        if (action === 'clear_old_sessions') {
            const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000).toISOString();
            const sessions = await base44.asServiceRole.entities.UserSession.list('-last_seen', 500);
            const oldSessions = sessions.filter(s => s.last_seen < oneHourAgo);
            for (const s of oldSessions) {
                await base44.asServiceRole.entities.UserSession.delete(s.id);
            }
            return Response.json({ success: true, cleared: oldSessions.length });
        }

        return Response.json({ error: 'Unknown action' }, { status: 400 });
    } catch (error) {
        return Response.json({ error: error.message }, { status: 500 });
    }
});