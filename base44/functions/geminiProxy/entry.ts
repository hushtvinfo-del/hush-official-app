import { createClientFromRequest } from 'npm:@base44/sdk@0.7.1';

Deno.serve(async (req) => {
    try {
        const base44 = createClientFromRequest(req);
        const user = await base44.auth.me();
        
        if (!user) {
            return Response.json({ error: 'Unauthorized' }, { status: 401 });
        }

        const { query, contentList } = await req.json();

        console.log(`\n🔍 USER SEARCH: "${query}"`);
        console.log(`📚 Library: ${contentList.length} items\n`);

        // Create a clean list of all titles
        const movieTitles = contentList.filter(item => item.type === 'movie').map(m => m.name);
        const seriesTitles = contentList.filter(item => item.type === 'series').map(s => s.name);

        console.log(`Movies in library: ${movieTitles.length}`);
        console.log(`Series in library: ${seriesTitles.length}\n`);

        // Use Base44's InvokeLLM instead of Gemini
        const prompt = `You are filtering a user's streaming library based on their search query.

USER SEARCH: "${query}"

AVAILABLE MOVIES:
${movieTitles.join('\n')}

AVAILABLE TV SERIES:
${seriesTitles.join('\n')}

Task: Return ONLY the titles from the lists above that match the user's search. Consider:
- Exact matches: "Narcos" → return "Narcos" if it exists
- Franchise searches: "Terminator movies" → return ALL Terminator titles from the movie list
- Actor searches: "Tom Hanks movies" → return movies featuring Tom Hanks from the movie list
- Theme searches: "shark movies" → return shark-related movies from the movie list

IMPORTANT: Only return titles that actually exist in the lists above. Do not make up titles.`;

        console.log('📤 Calling Base44 LLM...');

        const llmResponse = await base44.integrations.Core.InvokeLLM({
            prompt: prompt,
            response_json_schema: {
                type: "object",
                properties: {
                    matched_movies: {
                        type: "array",
                        items: { type: "string" }
                    },
                    matched_series: {
                        type: "array",
                        items: { type: "string" }
                    }
                }
            }
        });

        console.log('✅ LLM Response received\n');

        const matchedMovieTitles = llmResponse.matched_movies || [];
        const matchedSeriesTitles = llmResponse.matched_series || [];

        console.log(`📋 LLM matched:`);
        console.log(`   Movies: ${matchedMovieTitles.length}`);
        console.log(`   Series: ${matchedSeriesTitles.length}\n`);

        // Find the actual items from contentList
        const foundMovies = [];
        const foundSeries = [];

        for (const title of matchedMovieTitles) {
            const item = contentList.find(c => c.type === 'movie' && c.name.toLowerCase() === title.toLowerCase());
            if (item) {
                foundMovies.push(item);
                console.log(`   ✓ Movie: ${item.name}`);
            }
        }

        for (const title of matchedSeriesTitles) {
            const item = contentList.find(c => c.type === 'series' && c.name.toLowerCase() === title.toLowerCase());
            if (item) {
                foundSeries.push(item);
                console.log(`   ✓ Series: ${item.name}`);
            }
        }

        console.log(`\n🎬 RESULTS: ${foundMovies.length} movies, ${foundSeries.length} series\n`);

        return Response.json({ 
            movies: foundMovies, 
            series: foundSeries 
        });

    } catch (error) {
        console.error('❌ ERROR:', error.message);
        return Response.json({ 
            error: error.message, 
            movies: [], 
            series: [] 
        }, { status: 500 });
    }
});