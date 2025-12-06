const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

const API_KEY = Deno.env.get("TMDB_API_KEY");
const BASE_URL = "https://api.themoviedb.org/3";

// Helper function to clean up movie/series names for better TMDB matching
const cleanTitle = (title) => {
    if (!title) return '';
    
    let cleaned = title;
    
    // Remove year in parentheses like "(2023)"
    cleaned = cleaned.replace(/\s*\(\d{4}\)\s*/g, ' ');
    
    // Remove quality indicators
    cleaned = cleaned.replace(/\b(HD|4K|UHD|1080p|720p|BluRay|WEB-DL|WEBRip)\b/gi, '');
    
    // Remove brackets and their contents
    cleaned = cleaned.replace(/\[.*?\]/g, '');
    
    // Remove extra whitespace
    cleaned = cleaned.replace(/\s+/g, ' ').trim();
    
    return cleaned;
};

// Helper function to try multiple search strategies
const searchWithFallbacks = async (type, originalName, year) => {
    const searchStrategies = [
        { name: originalName, year: year },           // Original with year
        { name: cleanTitle(originalName), year: year }, // Cleaned with year
        { name: originalName, year: null },            // Original without year
        { name: cleanTitle(originalName), year: null }, // Cleaned without year
    ];
    
    console.log(`Trying to find "${originalName}" on TMDB with multiple strategies...`);
    
    for (const strategy of searchStrategies) {
        try {
            const searchUrl = new URL(`${BASE_URL}/search/${type}`);
            searchUrl.searchParams.set('api_key', API_KEY);
            searchUrl.searchParams.set('query', strategy.name);
            
            if (strategy.year) {
                searchUrl.searchParams.set(
                    type === 'movie' ? 'primary_release_year' : 'first_air_date_year', 
                    strategy.year
                );
            }
            
            console.log(`Searching TMDB: "${strategy.name}" ${strategy.year ? `(${strategy.year})` : '(no year)'}`);
            
            const searchRes = await fetch(searchUrl);
            if (!searchRes.ok) {
                console.log(`Search failed with status: ${searchRes.status}`);
                continue;
            }
            
            const searchData = await searchRes.json();
            
            if (searchData.results && searchData.results.length > 0) {
                console.log(`✓ Found match: "${searchData.results[0].title || searchData.results[0].name}"`);
                return searchData.results[0].id;
            }
            
            console.log(`No results for this strategy, trying next...`);
        } catch (e) {
            console.log(`Error with strategy: ${e.message}`);
            continue;
        }
    }
    
    return null;
};

Deno.serve(async (req) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: CORS_HEADERS });
    }

    try {
        const { type, name, year } = await req.json();
        
        if (!API_KEY) {
            throw new Error("TMDB_API_KEY is not set in environment variables.");
        }
        if (!type || !name) {
            throw new Error("Missing 'type' (movie/tv) or 'name' parameter.");
        }

        // Try to find the content with multiple search strategies
        const contentId = await searchWithFallbacks(type, name, year);
        
        if (!contentId) {
            return new Response(JSON.stringify({ 
                error: "No results found on TMDB after trying multiple search strategies.",
                query: { type, name, year },
                note: "The title from your IPTV provider may not match TMDB's database."
            }), {
                status: 404,
                headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
            });
        }

        // Fetch detailed information including credits
        const detailsUrl = new URL(`${BASE_URL}/${type}/${contentId}`);
        detailsUrl.searchParams.set('api_key', API_KEY);
        detailsUrl.searchParams.set('append_to_response', 'credits');

        const detailsRes = await fetch(detailsUrl);
        if (!detailsRes.ok) {
            throw new Error(`TMDB details fetch failed with status: ${detailsRes.status}`);
        }

        const detailsData = await detailsRes.json();
        
        console.log(`✓ Retrieved full details including cast (${detailsData.credits?.cast?.length || 0} actors)`);
        
        return new Response(JSON.stringify(detailsData), {
            status: 200,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
        });

    } catch(e) {
        console.error("TMDB fetch error:", e);
        return new Response(JSON.stringify({ 
            error: e.message,
            stack: e.stack 
        }), {
            status: 500,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
        });
    }
});