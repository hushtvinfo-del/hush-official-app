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
        const { collection_id } = await req.json();
        
        if (!API_KEY) {
            throw new Error("TMDB_API_KEY is not set in environment variables.");
        }
        
        if (!collection_id) {
            throw new Error("Missing 'collection_id' parameter.");
        }

        const collectionUrl = new URL(`${BASE_URL}/collection/${collection_id}`);
        collectionUrl.searchParams.set('api_key', API_KEY);

        const collectionRes = await fetch(collectionUrl);
        
        if (!collectionRes.ok) {
            const errorText = await collectionRes.text();
            throw new Error(`TMDB collection fetch failed with status: ${collectionRes.status} - ${errorText}`);
        }

        const collectionData = await collectionRes.json();
        
        return new Response(JSON.stringify(collectionData), {
            status: 200,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
        });

    } catch(e) {
        return new Response(JSON.stringify({ 
            error: e.message,
            details: e.stack 
        }), {
            status: 500,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
        });
    }
});