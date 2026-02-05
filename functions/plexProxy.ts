import { createClientFromRequest } from 'npm:@base44/sdk@0.8.6';

Deno.serve(async (req) => {
    try {
        const base44 = createClientFromRequest(req);
        const { action, sectionId, ratingKey } = await req.json();
        
        const plexUrl = Deno.env.get("PLEX_SERVER_URL");
        const plexToken = Deno.env.get("PLEX_TOKEN");

        if (!plexUrl || !plexToken) {
            return Response.json({ error: 'Plex credentials not configured' }, { status: 500 });
        }

        const baseUrl = plexUrl.replace(/\/$/, '');

        // Get all library sections
        if (action === 'get_libraries') {
            const response = await fetch(`${baseUrl}/library/sections?X-Plex-Token=${plexToken}`, {
                headers: { 'Accept': 'application/json' }
            });
            const data = await response.json();
            
            return Response.json({
                movies: data.MediaContainer.Directory?.filter(d => d.type === 'movie') || [],
                shows: data.MediaContainer.Directory?.filter(d => d.type === 'show') || []
            });
        }

        // Get all items in a library section
        if (action === 'get_library_items') {
            const response = await fetch(`${baseUrl}/library/sections/${sectionId}/all?X-Plex-Token=${plexToken}`, {
                headers: { 'Accept': 'application/json' }
            });
            const data = await response.json();
            return Response.json(data.MediaContainer.Metadata || []);
        }

        // Get metadata for a specific item
        if (action === 'get_metadata') {
            const response = await fetch(`${baseUrl}/library/metadata/${ratingKey}?X-Plex-Token=${plexToken}`, {
                headers: { 'Accept': 'application/json' }
            });
            const data = await response.json();
            return Response.json(data.MediaContainer.Metadata?.[0] || null);
        }

        return Response.json({ error: 'Invalid action' }, { status: 400 });
    } catch (error) {
        return Response.json({ error: error.message }, { status: 500 });
    }
});