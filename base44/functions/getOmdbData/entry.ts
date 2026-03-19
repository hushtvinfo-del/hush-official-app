import { createClientFromRequest } from 'npm:@base44/sdk@0.7.1';

Deno.serve(async (req) => {
    try {
        const base44 = createClientFromRequest(req);
        const user = await base44.auth.me();

        if (!user) {
            return Response.json({ error: 'Unauthorized' }, { status: 401 });
        }

        const OMDB_API_KEY = Deno.env.get("OMDB_API_KEY");
        if (!OMDB_API_KEY) {
            return Response.json({ error: 'OMDb API key not configured' }, { status: 500 });
        }

        const { title, year, type } = await req.json();
        
        if (!title) {
            return Response.json({ error: 'Title is required' }, { status: 400 });
        }

        // Build OMDb API URL
        const params = new URLSearchParams({
            apikey: OMDB_API_KEY,
            t: title,
            type: type || 'movie' // 'movie' or 'series'
        });

        if (year) {
            params.append('y', year);
        }

        const omdbUrl = `https://www.omdbapi.com/?${params.toString()}`;
        const response = await fetch(omdbUrl);
        const data = await response.json();

        if (data.Response === "False") {
            return Response.json({ error: data.Error || 'Not found' }, { status: 404 });
        }

        // Extract ratings
        const ratings = {
            imdb: data.imdbRating !== "N/A" ? data.imdbRating : null,
            rottenTomatoes: null,
            metacritic: null
        };

        // Parse Rotten Tomatoes score
        const rtRating = data.Ratings?.find(r => r.Source === "Rotten Tomatoes");
        if (rtRating) {
            ratings.rottenTomatoes = parseInt(rtRating.Value);
        }

        // Parse Metacritic score
        const mcRating = data.Ratings?.find(r => r.Source === "Metacritic");
        if (mcRating) {
            ratings.metacritic = parseInt(mcRating.Value);
        }

        return Response.json({
            title: data.Title,
            year: data.Year,
            rated: data.Rated,
            runtime: data.Runtime,
            genre: data.Genre,
            plot: data.Plot,
            ratings,
            imdbID: data.imdbID
        });

    } catch (error) {
        console.error('OMDb API error:', error);
        return Response.json({ error: error.message }, { status: 500 });
    }
});