import { createClientFromRequest } from 'npm:@base44/sdk@0.7.1';

const OPENSUBTITLES_API_KEY = Deno.env.get("OPENSUBTITLES_API_KEY");
const USER_AGENT = "HushTV v1.0";

Deno.serve(async (req) => {
    try {
        const base44 = createClientFromRequest(req);
        const user = await base44.auth.me();

        if (!user) {
            return Response.json({ error: 'Unauthorized' }, { status: 401 });
        }

        if (!OPENSUBTITLES_API_KEY) {
            console.error('OPENSUBTITLES_API_KEY not set');
            return Response.json({ 
                error: 'OpenSubtitles API key not configured',
                subtitles: []
            }, { status: 500 });
        }

        const body = await req.json();
        const { query, type, imdb_id, season, episode } = body;

        console.log('Subtitle search request:', { query, type, imdb_id, season, episode });

        if (!query && !imdb_id) {
            return Response.json({ 
                error: 'Either query or imdb_id is required',
                subtitles: []
            }, { status: 400 });
        }

        // Search for subtitles
        let searchUrl = 'https://api.opensubtitles.com/api/v1/subtitles?';
        
        if (imdb_id) {
            searchUrl += `imdb_id=${imdb_id}&`;
        } else {
            searchUrl += `query=${encodeURIComponent(query)}&`;
        }
        
        if (type === 'episode' && season && episode) {
            searchUrl += `season_number=${season}&episode_number=${episode}&`;
        }
        
        searchUrl += 'languages=en';

        console.log('Fetching from OpenSubtitles:', searchUrl);

        const searchResponse = await fetch(searchUrl, {
            headers: {
                'Api-Key': OPENSUBTITLES_API_KEY,
                'User-Agent': USER_AGENT,
                'Content-Type': 'application/json'
            }
        });

        console.log('OpenSubtitles response status:', searchResponse.status);

        if (!searchResponse.ok) {
            const errorText = await searchResponse.text();
            console.error('OpenSubtitles API error:', searchResponse.status, errorText);
            return Response.json({ 
                error: `OpenSubtitles API error: ${searchResponse.status}`,
                message: 'Unable to fetch subtitles at this time',
                subtitles: []
            }, { status: 200 }); // Return 200 so the app doesn't break
        }

        const searchData = await searchResponse.json();
        console.log('OpenSubtitles response data:', JSON.stringify(searchData).substring(0, 200));
        
        if (!searchData.data || searchData.data.length === 0) {
            return Response.json({ 
                subtitles: [],
                message: 'No subtitles found for this content'
            });
        }

        // Get the best subtitle (highest download count, prefer hearing impaired)
        const sortedSubtitles = searchData.data.sort((a, b) => {
            if (a.attributes.hearing_impaired && !b.attributes.hearing_impaired) return -1;
            if (!a.attributes.hearing_impaired && b.attributes.hearing_impaired) return 1;
            return b.attributes.download_count - a.attributes.download_count;
        });

        // Return top 5 subtitle options
        const subtitles = sortedSubtitles.slice(0, 5).map(sub => ({
            id: sub.id,
            language: sub.attributes.language,
            file_name: sub.attributes.files?.[0]?.file_name || 'subtitle.srt',
            download_url: sub.attributes.files?.[0]?.file_id,
            release: sub.attributes.release,
            hearing_impaired: sub.attributes.hearing_impaired,
            download_count: sub.attributes.download_count,
            rating: sub.attributes.ratings
        }));

        console.log(`Found ${subtitles.length} subtitles`);

        return Response.json({ 
            subtitles,
            total: searchData.total_count
        });

    } catch (error) {
        console.error('Subtitle fetch error:', error);
        return Response.json({ 
            error: error.message || 'Unknown error',
            subtitles: [],
            message: 'Failed to fetch subtitles. Please try again later.'
        }, { status: 200 }); // Return 200 to prevent app from breaking
    }
});