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
        const { topic } = await req.json();
        
        if (!API_KEY) {
            throw new Error("TMDB_API_KEY is not set in environment variables.");
        }
        if (!topic) {
            throw new Error("Missing 'topic' parameter.");
        }

        console.log(`\n${'='.repeat(70)}`);
        console.log(`🔎 TMDB TOPIC SEARCH: "${topic}"`);
        console.log(`${'='.repeat(70)}`);

        const allMovies = [];
        const allTV = [];
        
        // STRATEGY 1: Direct title search (for "terminator movies", search "terminator")
        console.log('\n📝 STRATEGY 1: Direct title search');
        try {
            const searchTerm = topic.replace(/\s+(movies|films|shows|series)$/i, '').trim();
            console.log(`   Searching for: "${searchTerm}"`);
            
            const [movieSearchRes, tvSearchRes] = await Promise.all([
                fetch(`${BASE_URL}/search/movie?api_key=${API_KEY}&query=${encodeURIComponent(searchTerm)}&page=1`),
                fetch(`${BASE_URL}/search/tv?api_key=${API_KEY}&query=${encodeURIComponent(searchTerm)}&page=1`)
            ]);
            
            if (movieSearchRes.ok) {
                const movieData = await movieSearchRes.json();
                if (movieData.results && movieData.results.length > 0) {
                    allMovies.push(...movieData.results);
                    console.log(`   ✅ Found ${movieData.results.length} movies`);
                    console.log(`   Top 5: ${movieData.results.slice(0, 5).map(m => m.title).join(', ')}`);
                } else {
                    console.log(`   ❌ No movies found`);
                }
            } else {
                console.log(`   ❌ Movie search failed: ${movieSearchRes.status}`);
            }
            
            if (tvSearchRes.ok) {
                const tvData = await tvSearchRes.json();
                if (tvData.results && tvData.results.length > 0) {
                    allTV.push(...tvData.results);
                    console.log(`   ✅ Found ${tvData.results.length} TV shows`);
                } else {
                    console.log(`   ❌ No TV shows found`);
                }
            } else {
                console.log(`   ❌ TV search failed: ${tvSearchRes.status}`);
            }
        } catch (searchError) {
            console.error('   ❌ Direct search error:', searchError.message);
        }
        
        // STRATEGY 2: Keyword/theme search (for genres/topics)
        console.log('\n🏷️  STRATEGY 2: Keyword/theme search');
        try {
            const keywordSearchRes = await fetch(`${BASE_URL}/search/keyword?api_key=${API_KEY}&query=${encodeURIComponent(topic)}`);
            
            if (keywordSearchRes.ok) {
                const keywordData = await keywordSearchRes.json();
                
                if (keywordData.results && keywordData.results.length > 0) {
                    const topKeywords = keywordData.results.slice(0, 3);
                    console.log(`   Found ${topKeywords.length} keyword(s): ${topKeywords.map(k => `"${k.name}"`).join(', ')}`);
                    
                    for (const keyword of topKeywords) {
                        const [moviesRes, tvRes] = await Promise.all([
                            fetch(`${BASE_URL}/discover/movie?api_key=${API_KEY}&with_keywords=${keyword.id}&sort_by=popularity.desc&page=1`),
                            fetch(`${BASE_URL}/discover/tv?api_key=${API_KEY}&with_keywords=${keyword.id}&sort_by=popularity.desc&page=1`)
                        ]);
                        
                        if (moviesRes.ok) {
                            const movieData = await moviesRes.json();
                            if (movieData.results) {
                                allMovies.push(...movieData.results);
                                console.log(`   ✅ Keyword "${keyword.name}": ${movieData.results.length} movies`);
                            }
                        }
                        
                        if (tvRes.ok) {
                            const tvData = await tvRes.json();
                            if (tvData.results) {
                                allTV.push(...tvData.results);
                                console.log(`   ✅ Keyword "${keyword.name}": ${tvData.results.length} TV shows`);
                            }
                        }
                    }
                } else {
                    console.log(`   ℹ️  No keywords found for "${topic}"`);
                }
            } else {
                console.log(`   ❌ Keyword search failed: ${keywordSearchRes.status}`);
            }
        } catch (keywordError) {
            console.error('   ❌ Keyword search error:', keywordError.message);
        }

        // Remove duplicates
        const uniqueMovies = Array.from(new Map(allMovies.map(m => [m.id, m])).values());
        const uniqueTV = Array.from(new Map(allTV.map(t => [t.id, t])).values());

        console.log(`\n📊 TOTALS AFTER DEDUPLICATION:`);
        console.log(`   Movies: ${uniqueMovies.length}`);
        console.log(`   TV Shows: ${uniqueTV.length}`);

        // Format results
        const movies = uniqueMovies
            .map(m => ({
                title: m.title || m.original_title,
                description: m.overview,
                year: m.release_date ? new Date(m.release_date).getFullYear() : null,
                popularity: m.popularity
            }))
            .sort((a, b) => b.popularity - a.popularity)
            .slice(0, 200);

        const series = uniqueTV
            .map(s => ({
                title: s.name || s.original_name,
                description: s.overview,
                year: s.first_air_date ? new Date(s.first_air_date).getFullYear() : null,
                popularity: s.popularity
            }))
            .sort((a, b) => b.popularity - a.popularity)
            .slice(0, 200);

        if (movies.length > 0) {
            console.log(`\n🎬 TOP 10 MOVIES:`);
            movies.slice(0, 10).forEach((m, i) => {
                console.log(`   ${i + 1}. ${m.title} (${m.year || 'N/A'})`);
            });
        }
        
        if (series.length > 0) {
            console.log(`\n📺 TOP 10 TV SHOWS:`);
            series.slice(0, 10).forEach((s, i) => {
                console.log(`   ${i + 1}. ${s.title} (${s.year || 'N/A'})`);
            });
        }
        
        console.log(`\n${'='.repeat(70)}`);
        console.log(`✅ SEARCH COMPLETE\n`);

        return new Response(JSON.stringify({ 
            movies, 
            series,
            topic 
        }), {
            status: 200,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
        });

    } catch(e) {
        console.error("\n❌ FATAL ERROR:", e.message);
        console.error(e.stack);
        return new Response(JSON.stringify({ 
            error: e.message,
            stack: e.stack 
        }), {
            status: 500,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
        });
    }
});