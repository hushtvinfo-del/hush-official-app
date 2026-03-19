import { createClientFromRequest } from 'npm:@base44/sdk@0.7.1';

Deno.serve(async (req) => {
    try {
        const base44 = createClientFromRequest(req);
        const user = await base44.auth.me();
        
        if (!user) {
            return Response.json({ error: 'Unauthorized' }, { status: 401 });
        }

        const { query, contentList } = await req.json();

        console.log(`\n${'='.repeat(60)}`);
        console.log(`🔍 AI SEARCH REQUEST`);
        console.log(`${'='.repeat(60)}`);
        console.log(`User Query: "${query}"`);
        console.log(`Total Items: ${contentList.length}`);
        
        const movies = contentList.filter(item => item.type === 'movie');
        const series = contentList.filter(item => item.type === 'series');
        
        console.log(`Movies: ${movies.length}, Series: ${series.length}`);
        console.log(`\n📋 SAMPLE TITLES (first 10 of each):`);
        console.log(`\nMovies:`);
        movies.slice(0, 10).forEach((m, i) => console.log(`  ${i + 1}. ${m.name}`));
        console.log(`\nSeries:`);
        series.slice(0, 10).forEach((s, i) => console.log(`  ${i + 1}. ${s.name}`));

        // Create title lists
        const movieTitles = movies.map(m => m.name);
        const seriesTitles = series.map(s => s.name);

        const prompt = `You are a content search assistant. A user is searching their streaming library.

USER SEARCH QUERY: "${query}"

AVAILABLE MOVIES (${movieTitles.length} total):
${movieTitles.join('\n')}

AVAILABLE TV SERIES (${seriesTitles.length} total):
${seriesTitles.join('\n')}

Return ONLY titles that match the user's query. Match based on:
- Exact title matches: "Narcos" → "Narcos"
- Franchise/collection: "Terminator movies" → return ALL titles with "Terminator" from movies
- Actor searches: "Tom Hanks movies" → return movies featuring that actor
- Genre/theme: "sci-fi movies" → return sci-fi titles

Return the EXACT titles as they appear in the lists above. Do not modify spelling or formatting.`;

        console.log(`\n🤖 CALLING BASE44 AI...`);

        const llmResponse = await base44.integrations.Core.InvokeLLM({
            prompt: prompt,
            response_json_schema: {
                type: "object",
                properties: {
                    matched_movies: {
                        type: "array",
                        items: { type: "string" },
                        description: "Exact movie titles from the list that match the query"
                    },
                    matched_series: {
                        type: "array",
                        items: { type: "string" },
                        description: "Exact series titles from the list that match the query"
                    }
                }
            }
        });

        console.log(`\n✅ AI RESPONSE RECEIVED`);
        console.log(`Matched Movies: ${(llmResponse.matched_movies || []).length}`);
        console.log(`Matched Series: ${(llmResponse.matched_series || []).length}`);

        const matchedMovieTitles = llmResponse.matched_movies || [];
        const matchedSeriesTitles = llmResponse.matched_series || [];

        console.log(`\n🎯 AI RETURNED TITLES:`);
        console.log(`\nMovies:`);
        matchedMovieTitles.forEach((title, i) => console.log(`  ${i + 1}. "${title}"`));
        console.log(`\nSeries:`);
        matchedSeriesTitles.forEach((title, i) => console.log(`  ${i + 1}. "${title}"`));

        // Find matching items with fuzzy matching
        const foundMovies = [];
        const foundSeries = [];

        console.log(`\n🔎 MATCHING TITLES TO LIBRARY...`);

        for (const aiTitle of matchedMovieTitles) {
            // Try exact match first
            let item = movies.find(m => m.name === aiTitle);
            
            // If no exact match, try case-insensitive
            if (!item) {
                item = movies.find(m => m.name.toLowerCase() === aiTitle.toLowerCase());
            }
            
            // If still no match, try contains
            if (!item) {
                item = movies.find(m => 
                    m.name.toLowerCase().includes(aiTitle.toLowerCase()) ||
                    aiTitle.toLowerCase().includes(m.name.toLowerCase())
                );
            }
            
            if (item) {
                foundMovies.push(item);
                console.log(`  ✅ Movie: "${aiTitle}" → "${item.name}"`);
            } else {
                console.log(`  ❌ Movie: "${aiTitle}" → NOT FOUND IN LIBRARY`);
            }
        }

        for (const aiTitle of matchedSeriesTitles) {
            // Try exact match first
            let item = series.find(s => s.name === aiTitle);
            
            // If no exact match, try case-insensitive
            if (!item) {
                item = series.find(s => s.name.toLowerCase() === aiTitle.toLowerCase());
            }
            
            // If still no match, try contains
            if (!item) {
                item = series.find(s => 
                    s.name.toLowerCase().includes(aiTitle.toLowerCase()) ||
                    aiTitle.toLowerCase().includes(s.name.toLowerCase())
                );
            }
            
            if (item) {
                foundSeries.push(item);
                console.log(`  ✅ Series: "${aiTitle}" → "${item.name}"`);
            } else {
                console.log(`  ❌ Series: "${aiTitle}" → NOT FOUND IN LIBRARY`);
            }
        }

        console.log(`\n📊 FINAL RESULTS:`);
        console.log(`Movies: ${foundMovies.length}`);
        console.log(`Series: ${foundSeries.length}`);
        console.log(`${'='.repeat(60)}\n`);

        return Response.json({ 
            movies: foundMovies, 
            series: foundSeries 
        });

    } catch (error) {
        console.error(`\n❌ ERROR:`, error.message);
        console.error(error.stack);
        return Response.json({ 
            error: error.message, 
            movies: [], 
            series: [] 
        }, { status: 500 });
    }
});