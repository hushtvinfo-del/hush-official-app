const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

const API_KEY = Deno.env.get("TMDB_API_KEY");
const BASE_URL = "https://api.themoviedb.org/3";

Deno.serve(async (req) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: CORS_HEADERS });
    }

    try {
        const { actorName } = await req.json();
        
        if (!API_KEY) {
            throw new Error("TMDB_API_KEY is not set");
        }
        if (!actorName) {
            throw new Error("Missing 'actorName' parameter");
        }

        console.log(`Searching for actor: ${actorName}`);

        // Step 1: Search for the person
        const searchUrl = new URL(`${BASE_URL}/search/person`);
        searchUrl.searchParams.set('api_key', API_KEY);
        searchUrl.searchParams.set('query', actorName);

        const searchRes = await fetch(searchUrl);
        if (!searchRes.ok) {
            throw new Error(`TMDB person search failed: ${searchRes.status}`);
        }

        const searchData = await searchRes.json();
        
        if (!searchData.results || searchData.results.length === 0) {
            return new Response(JSON.stringify({ 
                movies: [],
                series: [],
                message: "Actor not found on TMDB"
            }), {
                status: 200,
                headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
            });
        }

        const person = searchData.results[0];
        const personId = person.id;
        
        console.log(`Found actor: ${person.name} (ID: ${personId})`);

        // Step 2: Get movie and TV credits
        const [movieCreditsRes, tvCreditsRes] = await Promise.all([
            fetch(`${BASE_URL}/person/${personId}/movie_credits?api_key=${API_KEY}`),
            fetch(`${BASE_URL}/person/${personId}/tv_credits?api_key=${API_KEY}`)
        ]);

        if (!movieCreditsRes.ok || !tvCreditsRes.ok) {
            throw new Error("Failed to fetch actor credits");
        }

        const movieCredits = await movieCreditsRes.json();
        const tvCredits = await tvCreditsRes.json();

        // Extract titles from credits (cast roles only)
        const movies = (movieCredits.cast || []).map(movie => ({
            title: movie.title,
            year: movie.release_date ? new Date(movie.release_date).getFullYear() : null,
            tmdb_id: movie.id
        }));

        const series = (tvCredits.cast || []).map(show => ({
            title: show.name,
            year: show.first_air_date ? new Date(show.first_air_date).getFullYear() : null,
            tmdb_id: show.id
        }));

        console.log(`Found ${movies.length} movies and ${series.length} TV shows for ${person.name}`);

        return new Response(JSON.stringify({
            actor_name: person.name,
            movies: movies.slice(0, 100), // Limit to top 100
            series: series.slice(0, 100)
        }), {
            status: 200,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
        });

    } catch(e) {
        console.error("Actor search error:", e);
        return new Response(JSON.stringify({ 
            error: e.message,
            stack: e.stack 
        }), {
            status: 500,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
        });
    }
});