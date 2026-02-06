import { createClientFromRequest } from 'npm:@base44/sdk@0.8.6';

Deno.serve(async (req) => {
    try {
        const base44 = createClientFromRequest(req);
        const { action, sectionId, ratingKey, plexUrl, plexUsername, plexPassword, plexToken } = await req.json();
        
        if (!plexUrl) {
            return Response.json({ error: 'Plex URL not provided' }, { status: 400 });
        }

        let authToken = plexToken;

        // Authenticate with Plex to get token if not provided
        if (!authToken && plexUsername && plexPassword) {
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
            authToken = authData.user.authToken;
        }

        if (!authToken) {
            return Response.json({ error: 'Plex token or credentials required' }, { status: 400 });
        }

        const baseUrl = plexUrl.replace(/\/$/, '');

        // Get all library sections
        if (action === 'get_libraries') {
            const url = `${baseUrl}/library/sections?X-Plex-Token=${authToken}`;
            console.log('Fetching Plex libraries from:', url.replace(authToken, 'TOKEN'));
            
            const response = await fetch(url, {
                headers: { 
                    'Accept': 'application/json',
                    'X-Plex-Token': authToken
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
            const response = await fetch(`${baseUrl}/library/sections/${sectionId}/all?X-Plex-Token=${authToken}`, {
                headers: { 'Accept': 'application/json' }
            });
            const data = await response.json();
            const items = data.MediaContainer.Metadata || [];
            
            // Add full URLs for thumbnails
            const itemsWithUrls = items.map(item => ({
                ...item,
                thumb: item.thumb ? `${baseUrl}${item.thumb}?X-Plex-Token=${authToken}` : null
            }));
            
            return Response.json(itemsWithUrls);
        }

        // Get metadata for a specific item
        if (action === 'get_metadata') {
            const response = await fetch(`${baseUrl}/library/metadata/${ratingKey}?X-Plex-Token=${authToken}`, {
                headers: { 'Accept': 'application/json' }
            });
            const data = await response.json();
            const metadata = data.MediaContainer.Metadata?.[0] || null;
            
            if (metadata) {
                // Add full URLs for thumbnails and art
                if (metadata.thumb) {
                    metadata.thumb = `${baseUrl}${metadata.thumb}?X-Plex-Token=${authToken}`;
                }
                if (metadata.art) {
                    metadata.art = `${baseUrl}${metadata.art}?X-Plex-Token=${authToken}`;
                }
                
                // Add stream URL if it's a movie
                if (metadata.type === 'movie' && metadata.Media?.[0]?.Part?.[0]) {
                    const part = metadata.Media[0].Part[0];
                    metadata.streamUrl = `${baseUrl}${part.key}?download=1&X-Plex-Token=${authToken}`;
                    console.log('Movie stream URL:', metadata.streamUrl.replace(authToken, 'TOKEN'));
                }
                
                // Add episode stream URLs if it's a show
                if (metadata.type === 'show') {
                    // Fetch seasons for this show
                    const seasonsResponse = await fetch(`${baseUrl}/library/metadata/${ratingKey}/children?X-Plex-Token=${authToken}`, {
                        headers: { 'Accept': 'application/json' }
                    });
                    const seasonsData = await seasonsResponse.json();
                    const seasons = seasonsData.MediaContainer.Metadata || [];
                    
                    metadata.seasons = [];
                    for (const season of seasons) {
                        // Fetch episodes for each season
                        const episodesResponse = await fetch(`${baseUrl}/library/metadata/${season.ratingKey}/children?X-Plex-Token=${authToken}`, {
                            headers: { 'Accept': 'application/json' }
                        });
                        const episodesData = await episodesResponse.json();
                        const episodes = episodesData.MediaContainer.Metadata || [];
                        
                        metadata.seasons.push({
                            seasonNumber: season.index,
                            episodes: episodes.map(ep => {
                                const part = ep.Media?.[0]?.Part?.[0];
                                return {
                                    ratingKey: ep.ratingKey,
                                    title: ep.title,
                                    index: ep.index,
                                    thumb: ep.thumb ? `${baseUrl}${ep.thumb}?X-Plex-Token=${authToken}` : null,
                                    streamUrl: part?.key ? `${baseUrl}${part.key}?download=1&X-Plex-Token=${authToken}` : null
                                };
                            })
                        });
                    }
                }
            }
            
            return Response.json(metadata);
        }

        return Response.json({ error: 'Invalid action' }, { status: 400 });
    } catch (error) {
        return Response.json({ error: error.message }, { status: 500 });
    }
});