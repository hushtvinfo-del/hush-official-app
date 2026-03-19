const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

const RPDB_API_KEY = Deno.env.get("RPDB_API_KEY");
const TMDB_API_KEY = Deno.env.get("TMDB_API_KEY");

Deno.serve(async (req) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: CORS_HEADERS });
    }

    try {
        const { tmdb_id, type } = await req.json(); // type: 'movie' or 'tv'
        
        if (!RPDB_API_KEY) {
            throw new Error("RPDB_API_KEY is not set");
        }
        
        if (!TMDB_API_KEY) {
            throw new Error("TMDB_API_KEY is not set");
        }

        if (!tmdb_id || !type) {
            throw new Error("Missing tmdb_id or type parameter");
        }

        // Fetch RPDB rating poster
        const rpdbUrl = `https://api.ratingposterdb.com/${tmdb_id}/${type}/rating?apikey=${RPDB_API_KEY}`;
        
        let ratingPosterUrl = null;
        let rpdbRating = null;
        
        try {
            const rpdbRes = await fetch(rpdbUrl);
            if (rpdbRes.ok) {
                const rpdbData = await rpdbRes.json();
                ratingPosterUrl = rpdbData.poster_url || null;
                rpdbRating = rpdbData.rating || null;
            }
        } catch (rpdbError) {
            console.warn('RPDB fetch failed:', rpdbError.message);
        }

        // Fetch TMDB details for reviews/ratings
        const tmdbUrl = `https://api.themoviedb.org/3/${type}/${tmdb_id}?api_key=${TMDB_API_KEY}`;
        const tmdbRes = await fetch(tmdbUrl);
        
        if (!tmdbRes.ok) {
            throw new Error(`TMDB fetch failed: ${tmdbRes.status}`);
        }

        const tmdbData = await tmdbRes.json();
        
        // Get reviews from TMDB
        const reviewsUrl = `https://api.themoviedb.org/3/${type}/${tmdb_id}/reviews?api_key=${TMDB_API_KEY}`;
        const reviewsRes = await fetch(reviewsUrl);
        let reviews = [];
        
        if (reviewsRes.ok) {
            const reviewsData = await reviewsRes.json();
            reviews = (reviewsData.results || []).slice(0, 3).map(review => ({
                author: review.author,
                content: review.content,
                rating: review.author_details?.rating,
                created_at: review.created_at
            }));
        }

        return new Response(JSON.stringify({
            tmdb_rating: tmdbData.vote_average,
            tmdb_vote_count: tmdbData.vote_count,
            rpdb_poster_url: ratingPosterUrl,
            rpdb_rating: rpdbRating,
            reviews: reviews,
            tagline: tmdbData.tagline,
        }), {
            status: 200,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
        });

    } catch(e) {
        console.error("RPDB data fetch error:", e);
        return new Response(JSON.stringify({ 
            error: e.message,
            stack: e.stack 
        }), {
            status: 500,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
        });
    }
});