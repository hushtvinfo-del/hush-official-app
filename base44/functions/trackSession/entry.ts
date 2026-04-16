import { createClientFromRequest } from 'npm:@base44/sdk@0.8.25';

Deno.serve(async (req) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type' } });
    }

    try {
        const base44 = createClientFromRequest(req);
        const body = await req.json();
        const { session_id, account_name, username, device_type, current_content, content_type, playlist_host, account_expiry, max_connections, active_connections } = body;

        // Get IP from request headers
        const ip_address = req.headers.get('x-forwarded-for')?.split(',')[0]?.trim() 
            || req.headers.get('x-real-ip') 
            || 'unknown';
        const user_agent = req.headers.get('user-agent') || 'unknown';

        if (!session_id) {
            return Response.json({ error: 'session_id required' }, { status: 400 });
        }

        // Try to find existing session
        const existing = await base44.asServiceRole.entities.UserSession.filter({ session_id }, '-last_seen', 1);

        const sessionData = {
            session_id,
            account_name: account_name || 'Unknown',
            username: username || '',
            ip_address,
            user_agent,
            device_type: device_type || 'unknown',
            current_content: current_content || '',
            content_type: content_type || 'idle',
            playlist_host: playlist_host || '',
            last_seen: new Date().toISOString(),
            account_expiry: account_expiry || '',
            max_connections: max_connections || '',
            active_connections: active_connections || ''
        };

        if (existing.length > 0) {
            await base44.asServiceRole.entities.UserSession.update(existing[0].id, sessionData);
        } else {
            await base44.asServiceRole.entities.UserSession.create(sessionData);
        }

        return Response.json({ success: true });
    } catch (error) {
        return Response.json({ error: error.message }, { status: 500 });
    }
});