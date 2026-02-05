import { createClientFromRequest } from 'npm:@base44/sdk@0.8.6';

Deno.serve(async (req) => {
    try {
        const base44 = createClientFromRequest(req);
        const { action, sectionId, ratingKey } = await req.json();
        
        const plexUrl = Deno.env.get("PLEX_SERVER_URL");
        const plexUsername = Deno.env.get("PLEX_USERNAME");
        const plexPassword = Deno.env.get("PLEX_PASSWORD");

        if (!plexUrl || !plexUsername || !plexPassword) {
            return Response.json({ error: 'Plex credentials not configured' }, { status: 500 });
        }

        // Authenticate with Plex to get token
        const authResponse = await fetch('https://plex.tv/users/sign_in.json', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Plex-Client-Identifier': 'hushtv-app',
                'X-Plex-Product': 'HushTV',
                'X-Plex-Version': '1.0'
            },
            body: new URLSearchParams({
                'user[login]': plexUsername,
                'user[password]': plexPassword
            })
        });

        if (!authResponse.ok) {
            return Response.json({ 
                error: 'Failed to authenticate with Plex',
                details: await authResponse.text()
            }, { status: 401 });
        }

        const authData = await authResponse.json();
        const plexToken = authData.user.authToken;

        const baseUrl = plexUrl.replace(/\/$/, '');

        // Get all library sections
        if (action === 'get_libraries') {
            const url = `${baseUrl}/library/sections?X-Plex-Token=${plexToken}`;
            console.log('Fetching Plex libraries from:', url.replace(plexToken, 'TOKEN'));
            
            const response = await fetch(url, {
                headers: { 
                    'Accept': 'application/json',
                    'X-Plex-Token': plexToken
                }
            });
            
            const text = await response.text();
            console.log('Response status:', response.status);
            console.log('Response preview:', text.substring(0, 200));
            
            if (!response.ok) {
                return Response.json({ 
                    error: `Plex server returned status ${response.status}`,
                    details: text.substring(0, 500)
                }, { status: 500 });
            }
            
            const data = JSON.parse(text);
            
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