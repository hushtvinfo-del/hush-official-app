import { createClientFromRequest } from 'npm:@base44/sdk@0.8.25';

Deno.serve(async (req) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type' } });
    }

    try {
        const base44 = createClientFromRequest(req);
        const body = await req.json();
        const { admin_token } = body;

        // Simple admin token check
        if (admin_token !== 'hushtv_admin_Masterpan1') {
            return Response.json({ error: 'Unauthorized' }, { status: 401 });
        }

        // Get active sessions (last seen within 10 minutes)
        const tenMinutesAgo = new Date(Date.now() - 10 * 60 * 1000).toISOString();
        const allSessions = await base44.asServiceRole.entities.UserSession.list('-last_seen', 500);
        const activeSessions = allSessions.filter(s => s.last_seen > tenMinutesAgo);

        // Get active broadcast messages
        const broadcasts = await base44.asServiceRole.entities.BroadcastMessage.filter({ is_active: true }, '-created_date', 20);

        // Get app configs
        const configs = await base44.asServiceRole.entities.AppConfig.list('-created_date', 100);

        // Get recent watch progress
        const watchProgress = await base44.asServiceRole.entities.WatchProgress.list('-updated_date', 100);

        return Response.json({
            activeSessions,
            allSessions: allSessions.slice(0, 200),
            broadcasts,
            configs,
            watchProgress,
            stats: {
                total_connected: activeSessions.length,
                total_sessions_ever: allSessions.length,
                live_watchers: activeSessions.filter(s => s.content_type === 'live').length,
                movie_watchers: activeSessions.filter(s => s.content_type === 'movie').length,
                series_watchers: activeSessions.filter(s => s.content_type === 'series').length,
                idle_users: activeSessions.filter(s => s.content_type === 'idle').length
            }
        });
    } catch (error) {
        return Response.json({ error: error.message }, { status: 500 });
    }
});