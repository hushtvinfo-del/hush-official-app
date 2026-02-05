import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { base44 } from '@/api/base44Client';
import { Link, useNavigate } from 'react-router-dom';
import { createPageUrl } from '@/utils';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion';
import { ArrowLeft, Search, Film, Tv, Clapperboard, Loader2, Sparkles, AlertCircle, Zap, Bug } from 'lucide-react';
import { Badge } from '@/components/ui/badge'; // NEW: Added Badge import

const getPlaylistFromLocal = (playlistId) => {
    try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        return localPlaylists.find(p => p.id === playlistId);
    } catch(e) { return null; }
};

// Title matching utilities
const normalizeTitle = (title) => {
    if (!title) return '';
    return title
        .toLowerCase()
        .trim()
        .replace(/[\[\(].*?[\]\)]/g, '')
        .replace(/\b(hd|4k|uhd|1080p|720p|480p|bluray|blu-ray|web-?dl|webrip|dvdrip|brrip|hdtv)\b/gi, '')
        .replace(/[._-]+/g, ' ')
        .replace(/\b(19|20)\d{2}\b/g, '')
        .replace(/^(the|a|an)\s+/i, '')
        .replace(/\s+/g, ' ')
        .trim();
};

const titlesMatch = (libraryTitle, aiTitle) => {
    const norm1 = normalizeTitle(libraryTitle);
    const norm2 = normalizeTitle(aiTitle);
    
    if (!norm1 || !norm2) return false;
    
    if (norm1 === norm2) return true;
    
    const lengthRatio = Math.min(norm1.length, norm2.length) / Math.max(norm1.length, norm2.length);
    
    if (lengthRatio > 0.8) {
        if (norm1.includes(norm2) || norm2.includes(norm1)) return true;
    }
    
    const words1 = norm1.split(' ').filter(w => w.length > 2);
    const words2 = norm2.split(' ').filter(w => w.length > 2);
    
    if (words1.length >= 2 && words2.length >= 2) {
        const matchingWords = words1.filter(w => words2.includes(w)).length;
        const totalWords = Math.max(words1.length, words2.length);
        
        if (matchingWords >= totalWords * 0.7 && matchingWords >= 2) {
            return true;
        }
    }
    
    return false;
};

// Fuzzy search to find candidates before AI
const fuzzySearchLibrary = (library, query, limit = 100) => {
    const queryLower = query.toLowerCase().trim();
    const queryWords = queryLower.split(/\s+/).filter(w => w.length > 2);
    
    console.log('\n🔎 FUZZY SEARCH STARTING');
    console.log('   Query:', query);
    console.log('   Query (lowercase):', queryLower);
    console.log('   Query words (>2 chars):', queryWords);
    console.log('   Library size:', library.length);
    
    const scored = library.map(item => {
        const titleNorm = normalizeTitle(item.name);
        const titleLower = item.name.toLowerCase();
        let score = 0;
        let reasons = [];
        
        // Exact match in original title
        if (titleLower.includes(queryLower)) {
            score += 100;
            reasons.push(`exact in original (+100)`);
        }
        
        // Exact match in normalized title
        if (titleNorm.includes(normalizeTitle(query))) {
            score += 90;
            reasons.push(`exact in normalized (+90)`);
        }
        
        // Word matching
        const titleWords = titleNorm.split(/\s+/).filter(w => w.length > 2);
        const matchingWords = queryWords.filter(qw => titleWords.some(tw => tw.includes(qw) || qw.includes(tw)));
        if (matchingWords.length > 0) {
            const wordScore = matchingWords.length * 30;
            score += wordScore;
            reasons.push(`${matchingWords.length} word matches (+${wordScore})`);
        }
        
        // Partial word matching
        for (const qw of queryWords) {
            for (const tw of titleWords) {
                if (tw.startsWith(qw) || qw.startsWith(tw)) {
                    score += 20;
                    reasons.push(`partial word match (+20)`);
                }
            }
        }
        
        return { item, score, reasons, titleNorm, titleLower };
    });
    
    const candidates = scored
        .filter(s => s.score > 0)
        .sort((a, b) => b.score - a.score)
        .slice(0, limit);
    
    console.log('   Total scored items:', scored.length);
    console.log('   Items with score > 0:', scored.filter(s => s.score > 0).length);
    console.log('   Returning top', Math.min(limit, candidates.length), 'candidates\n');
    
    if (candidates.length > 0) {
        console.log('📊 TOP 10 CANDIDATES WITH SCORES:');
        candidates.slice(0, 10).forEach((c, i) => {
            console.log(`   ${i+1}. "${c.item.name}"`);
            console.log(`      Score: ${c.score}`);
            console.log(`      Normalized: "${c.titleNorm}"`);
            console.log(`      Reasons: ${c.reasons.join(', ')}`);
        });
    } else {
        console.log('❌ NO CANDIDATES FOUND!');
        console.log('   Showing sample titles from library (first 10):');
        library.slice(0, 10).forEach((item, i) => {
            console.log(`   ${i+1}. "${item.name}"`);
            console.log(`      Normalized: "${normalizeTitle(item.name)}"`);
        });
    }
    
    return candidates.map(c => c.item);
};

// Simple internal search - just keyword matching
const fetchInternalSearchResults = async (playlist, query, setStatus, contentType = null) => { // MODIFIED: Added contentType
    if (!playlist || !query) return { movies: [], series: [], liveChannels: [], searchType: 'none' };

    console.log('Starting Internal Search for:', query, '| Content Type:', contentType || 'all'); // MODIFIED
    
    setStatus('Searching your library...');
    const lowerCaseQuery = query.toLowerCase();
    
    // Only fetch what we need based on contentType
    const fetchPromises = [];
    let vodResPromise = Promise.resolve({ data: [] });
    let seriesResPromise = Promise.resolve({ data: [] });
    let channelsResPromise = Promise.resolve({ data: [] });
    
    if (!contentType || contentType === 'movie') {
        vodResPromise = base44.functions.invoke('xtreamProxy', { 
            host: playlist.host, 
            username: playlist.username, 
            password: playlist.password, 
            params: { action: 'get_vod_streams' } 
        });
    }
    fetchPromises.push(vodResPromise);
    
    if (!contentType || contentType === 'series') {
        seriesResPromise = base44.functions.invoke('xtreamProxy', { 
            host: playlist.host, 
            username: playlist.username, 
            password: playlist.password, 
            params: { action: 'get_series' } 
        });
    }
    fetchPromises.push(seriesResPromise);
    
    if (!contentType) { // Only fetch live channels if no specific content type is requested
        channelsResPromise = base44.functions.invoke('xtreamProxy', { 
            host: playlist.host, 
            username: playlist.username, 
            password: playlist.password, 
            params: { action: 'get_live_streams' } 
        });
    }
    fetchPromises.push(channelsResPromise);
    
    const [vodRes, seriesRes, channelsRes] = await Promise.all(fetchPromises);
    
    const allMovies = vodRes.data || [];
    const allSeries = seriesRes.data || [];
    const allChannels = channelsRes.data || [];
    
    const movies = allMovies.filter(c => c.name?.toLowerCase().includes(lowerCaseQuery));
    const series = allSeries.filter(c => c.name?.toLowerCase().includes(lowerCaseQuery));
    
    let liveChannels = [];
    
    if (!contentType && playlist.epg_url && allChannels.length > 0) { // MODIFIED: Only check EPG if no contentType
        try {
            setStatus('Checking live TV...');
            
            const tvgIds = allChannels.map(c => c.epg_channel_id).filter(Boolean);
            if (tvgIds.length > 0) {
                const epgRes = await base44.functions.invoke('fetchEPG', {
                    epg_url: playlist.epg_url,
                    tvg_ids: [...new Set(tvgIds)]
                });
                
                const epgData = epgRes.data || {};
                
                for (const channel of allChannels) {
                    const epg = epgData[channel.epg_channel_id];
                    if (epg && epg.title) {
                        const programMatchesQuery = epg.title.toLowerCase().includes(lowerCaseQuery) || 
                            (epg.desc && epg.desc.toLowerCase().includes(lowerCaseQuery));
                        
                        if (programMatchesQuery) {
                            liveChannels.push({
                                ...channel,
                                currentProgram: epg
                            });
                        }
                    }
                }
            }
        } catch (epgError) {
            console.warn('EPG fetch failed:', epgError);
            liveChannels = allChannels.filter(c => c.name?.toLowerCase().includes(lowerCaseQuery));
        }
    } else if (!contentType) { // MODIFIED: Only include if no contentType
        liveChannels = allChannels.filter(c => c.name?.toLowerCase().includes(lowerCaseQuery));
    }
    
    setStatus('');
    return { 
        movies: movies.slice(0, 100), 
        series: series.slice(0, 100),
        liveChannels,
        searchType: 'internal'
    };
};

// Smarter AI-powered search with pre-filtering
const fetchAiSearchResults = async (playlist, query, setStatus, setDebugInfo, contentType = null) => { // MODIFIED: Added contentType
    if (!playlist || !query) return { movies: [], series: [], liveChannels: [], searchType: 'none' };

    console.log('\n========================================');
    console.log('🔍 Starting SMART AI Search for:', query);
    console.log('🎯 Content Type Filter:', contentType || 'all'); // NEW
    console.log('========================================\n');
    
    setStatus('Loading your library...');
    setDebugInfo({ step: 'Loading library...', originalQuery: query, contentType: contentType || 'all' }); // MODIFIED: Added contentType
    
    try {
        // Step 1: Only load what we need based on contentType
        const fetchPromises = [];
        let vodResPromise = Promise.resolve({ data: [] });
        let seriesResPromise = Promise.resolve({ data: [] });
        
        if (!contentType || contentType === 'movie') {
            vodResPromise = base44.functions.invoke('xtreamProxy', { 
                host: playlist.host, 
                username: playlist.username, 
                password: playlist.password, 
                params: { action: 'get_vod_streams' } 
            });
        }
        fetchPromises.push(vodResPromise);
        
        if (!contentType || contentType === 'series') {
            seriesResPromise = base44.functions.invoke('xtreamProxy', { 
                host: playlist.host, 
                username: playlist.username, 
                password: playlist.password, 
                params: { action: 'get_series' } 
            });
        }
        fetchPromises.push(seriesResPromise);
        
        const [vodRes, seriesRes] = await Promise.all(fetchPromises);
        
        const allMovies = vodRes.data || [];
        const allSeries = seriesRes.data || [];
        
        console.log('📚 Library loaded:', allMovies.length, 'movies,', allSeries.length, 'series');
        
        setDebugInfo({ // MODIFIED: Updated this setDebugInfo to be a new object, not based on previous, for initial state setting
            step: 'Library loaded',
            originalQuery: query,
            contentType: contentType || 'all',
            libraryMoviesCount: allMovies.length,
            librarySeriesCount: allSeries.length,
            sampleMovies: allMovies.slice(0, 10).map(m => m.name),
            sampleSeries: allSeries.slice(0, 10).map(s => s.name)
        });
        
        setStatus('Finding candidates...');
        
        // Step 2: Use fuzzy search to narrow down candidates
        // Ensure fuzzy search is only called if there are items to search through
        const movieCandidates = allMovies.length > 0 ? fuzzySearchLibrary(allMovies, query, 100) : []; // MODIFIED
        const seriesCandidates = allSeries.length > 0 ? fuzzySearchLibrary(allSeries, query, 50) : []; // MODIFIED
        
        console.log('📊 Fuzzy search results:', movieCandidates.length, 'movie candidates,', seriesCandidates.length, 'series candidates');
        
        setDebugInfo(prev => ({
            ...prev,
            step: 'Candidates found',
            movieCandidatesCount: movieCandidates.length,
            seriesCandidatesCount: seriesCandidates.length,
            sampleCandidates: movieCandidates.slice(0, 10).map(m => m.name)
        }));
        
        // If we have good fuzzy results, use them directly
        if (movieCandidates.length > 0 || seriesCandidates.length > 0) {
            // For very specific queries (like "Harry Potter"), fuzzy search might be enough
            const queryWords = query.toLowerCase().split(/\s+/).filter(w => w.length > 2);
            
            // Check if this looks like a specific title search (2+ words, likely a movie/series name)
            if (queryWords.length >= 2) {
                console.log('✅ Specific title search detected, using fuzzy results directly');
                
                setDebugInfo(prev => ({
                    ...prev,
                    step: 'Using fuzzy search results',
                    finalMoviesCount: movieCandidates.length,
                    finalSeriesCount: seriesCandidates.length,
                    finalMovieTitles: movieCandidates.map(m => m.name),
                    finalSeriesTitles: seriesCandidates.map(s => s.name)
                }));
                
                setStatus('');
                
                return {
                    movies: movieCandidates,
                    series: seriesCandidates,
                    liveChannels: [],
                    searchType: 'ai',
                    aiUnderstanding: `Found titles matching "${query}"${contentType ? ` (${contentType}s only)` : ''}` // MODIFIED
                };
            }
        }
        
        // Step 3: If fuzzy search found candidates, validate/refine with AI
        if (movieCandidates.length > 0 || seriesCandidates.length > 0) {
            setStatus('Validating with AI...');
            
            const aiPrompt = `User searched for: "${query}"${contentType ? ` in ${contentType}s` : ''}

I've already narrowed down these candidate titles from their library:

${movieCandidates.length > 0 ? `MOVIE CANDIDATES (${movieCandidates.length} total, showing top 50):
${movieCandidates.slice(0, 50).map(m => `- ${m.name}`).join('\n')}` : ''}

${seriesCandidates.length > 0 ? `SERIES CANDIDATES (${seriesCandidates.length} total, showing top 30):
${seriesCandidates.slice(0, 30).map(s => `- ${s.name}`).join('\n')}` : ''}

Your task:
- Review these candidates and select ALL that match the search query
- For franchise searches (like "Harry Potter"), include ALL related titles
- Return the EXACT titles as written above
- If searching for a person/actor, include all movies that might feature them

Important: These are already filtered candidates, so be inclusive rather than exclusive.`;

            const aiResult = await base44.integrations.Core.InvokeLLM({
                prompt: aiPrompt,
                response_json_schema: {
                    type: "object",
                    properties: {
                        matched_movies: {
                            type: "array",
                            items: { type: "string" },
                            description: "EXACT movie titles from candidates that match"
                        },
                        matched_series: {
                            type: "array",
                            items: { type: "string" },
                            description: "EXACT series titles from candidates that match"
                        },
                        reasoning: {
                            type: "string",
                            description: "Brief explanation of matching criteria"
                        }
                    },
                    required: ["matched_movies", "matched_series"]
                }
            });
            
            console.log('🤖 AI validation:', aiResult.matched_movies?.length || 0, 'movies,', aiResult.matched_series?.length || 0, 'series');
            console.log('💭 AI reasoning:', aiResult.reasoning);
            
            // Step 4: Match AI selections with library objects
            const matchedMovies = [];
            for (const aiTitle of (aiResult.matched_movies || [])) {
                const found = movieCandidates.find(m => 
                    m.name.toLowerCase().trim() === aiTitle.toLowerCase().trim() ||
                    titlesMatch(m.name, aiTitle)
                );
                if (found && !matchedMovies.some(m => m.stream_id === found.stream_id)) {
                    matchedMovies.push(found);
                }
            }
            
            const matchedSeries = [];
            for (const aiTitle of (aiResult.matched_series || [])) {
                const found = seriesCandidates.find(s => 
                    s.name.toLowerCase().trim() === aiTitle.toLowerCase().trim() ||
                    titlesMatch(s.name, aiTitle)
                );
                if (found && !matchedSeries.some(s => s.series_id === found.series_id)) {
                    matchedSeries.push(found);
                }
            }
            
            console.log('📦 Final Results:', matchedMovies.length, 'movies,', matchedSeries.length, 'series');
            
            setDebugInfo(prev => ({
                ...prev,
                step: 'AI validation complete',
                aiUnderstanding: aiResult.reasoning,
                aiFoundMovies: aiResult.matched_movies?.length || 0,
                aiFoundSeries: aiResult.matched_series?.length || 0,
                aiMovieTitles: aiResult.matched_movies || [],
                finalMoviesCount: matchedMovies.length,
                finalSeriesCount: matchedSeries.length,
                finalMovieTitles: matchedMovies.map(m => m.name),
                finalSeriesTitles: matchedSeries.map(s => s.name)
            }));
            
            setStatus('');
            
            return {
                movies: matchedMovies,
                series: matchedSeries,
                liveChannels: [],
                searchType: 'ai',
                aiUnderstanding: aiResult.reasoning || `Found titles matching "${query}"${contentType ? ` (${contentType}s only)` : ''}` // MODIFIED
            };
        }
        
        // Step 5: No candidates found via fuzzy search - return empty
        console.log('❌ No candidates found via fuzzy search');
        
        setDebugInfo(prev => ({
            ...prev,
            step: 'No candidates found',
            finalMoviesCount: 0,
            finalSeriesCount: 0
        }));
        
        setStatus('');
        
        return {
            movies: [],
            series: [],
            liveChannels: [],
            searchType: 'ai',
            aiUnderstanding: `No titles found matching "${query}"${contentType ? ` in ${contentType}s` : ''}` // MODIFIED
        };
        
    } catch (error) {
        console.error('❌ AI search failed:', error);
        setDebugInfo(prev => ({ ...prev, step: 'Error', error: error.message }));
        throw error;
    }
};

// Helper function to check live TV
const checkLiveTV = async (playlist, allChannels, tmdbMovies, tmdbSeries, setStatus) => {
    const matchedLiveChannels = [];
    
    if (playlist.epg_url && allChannels.length > 0) {
        try {
            setStatus('Checking live TV...');
            const tvgIds = allChannels.map(c => c.epg_channel_id).filter(Boolean);
            if (tvgIds.length > 0) {
                const epgRes = await base44.functions.invoke('fetchEPG', {
                    epg_url: playlist.epg_url,
                    tvg_ids: [...new Set(tvgIds)]
                });
                
                const epgData = epgRes.data || {};
                
                for (const channel of allChannels) {
                    const epg = epgData[channel.epg_channel_id];
                    if (epg && epg.title) {
                        for (const movie of tmdbMovies) {
                            if (titlesMatch(epg.title, movie.title)) {
                                matchedLiveChannels.push({
                                    ...channel,
                                    currentProgram: epg
                                });
                                break;
                            }
                        }
                        for (const show of tmdbSeries) {
                            if (titlesMatch(epg.title, show.title)) {
                                matchedLiveChannels.push({
                                    ...channel,
                                    currentProgram: epg
                                });
                                break;
                            }
                        }
                    }
                }
            }
        } catch (epgError) {
            console.warn('EPG check failed:', epgError);
        }
    }
    
    return matchedLiveChannels;
};

export default function GlobalSearch() {
    const navigate = useNavigate();
    const urlParams = new URLSearchParams(window.location.search);
    const playlistId = urlParams.get('playlistId');
    const initialQuery = urlParams.get('q') || '';
    const initialMode = urlParams.get('mode') || 'internal';
    const contentType = urlParams.get('contentType'); // NEW: Get content type filter
    const [searchQuery, setSearchQuery] = useState(initialQuery);
    const [submittedQuery, setSubmittedQuery] = useState(initialQuery);
    const [searchMode, setSearchMode] = useState(initialMode);
    const [loadingStatus, setLoadingStatus] = useState('');
    const [debugInfo, setDebugInfo] = useState({});
    
    const { data: playlist } = useQuery({
      queryKey: ['playlist', playlistId],
      queryFn: () => getPlaylistFromLocal(playlistId),
      enabled: !!playlistId
    });

    // Auto-trigger search when coming from cast links
    React.useEffect(() => {
        if (initialQuery && initialMode === 'ai' && !submittedQuery) {
            setSubmittedQuery(initialQuery);
            setSearchMode('ai');
        }
    }, [initialQuery, initialMode, submittedQuery]);

    const { data: searchResults, isLoading, isError, error } = useQuery({
        queryKey: ['search', playlistId, submittedQuery, searchMode, contentType], // MODIFIED: Added contentType to queryKey
        queryFn: () => searchMode === 'ai' 
            ? fetchAiSearchResults(playlist, submittedQuery, setLoadingStatus, setDebugInfo, contentType) // MODIFIED: Pass contentType
            : fetchInternalSearchResults(playlist, submittedQuery, setLoadingStatus, contentType), // MODIFIED: Pass contentType
        enabled: !!playlistId && !!submittedQuery,
        staleTime: 5 * 60 * 1000,
        retry: 0,
        refetchOnWindowFocus: false,
    });
    
    const handleSearchSubmit = (mode) => {
        setSearchMode(mode);
        setSubmittedQuery(searchQuery);
        setDebugInfo({}); // Clear debug info on new search
    };
    
    const constructStreamUrl = (host, username, password, streamId) => {
        let fullHost = host;
        if (!/^https?:\/\//i.test(host)) fullHost = `http://${fullHost}`;
        const u = new URL(fullHost);
        return `${u.protocol}//${u.host}/live/${username}/${password}/${streamId}.m3u8`;
    };

    const totalResults = (searchResults?.movies?.length || 0) + (searchResults?.series?.length || 0) + (searchResults?.liveChannels?.length || 0);

    return (
        <div className="min-h-screen p-4 md:p-8">
            <div className="max-w-7xl mx-auto">
                <Button variant="ghost" onClick={() => navigate(createPageUrl(`MainMenu?playlistId=${playlistId}`))} className="mb-6 text-purple-300 hover:text-white hover:bg-purple-500/20">
                    <ArrowLeft className="w-4 h-4 mr-2" />
                    Back to Main Menu
                </Button>
                
                <div className="flex flex-col justify-between items-start mb-6 gap-4">
                    <div>
                        <h1 className="text-3xl md:text-4xl font-bold text-white mb-2 flex items-center gap-3">
                            <Search className="text-purple-400" />
                            Smart Search
                            {contentType && <Badge variant="outline" className="text-orange-400 border-orange-400/40"> {/* NEW */}
                                {contentType}s only
                            </Badge>}
                        </h1>
                        <p className="text-purple-300">
                            {contentType 
                                ? `Searching ${contentType}s - faster results!` 
                                : 'Choose your search method below'
                            }
                        </p> {/* MODIFIED */}
                    </div>
                </div>
                
                <div className="relative max-w-2xl mb-4">
                    <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
                    <Input
                        type="text"
                        placeholder="Search for movies, series, channels, actors, or describe what you want..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && handleSearchSubmit('internal')}
                        className="pl-10 text-lg bg-gray-800/50 border-purple-500/30 text-white placeholder:text-gray-500 focus:border-purple-500"
                    />
                </div>
                
                <div className="flex gap-3 mb-8 max-w-2xl">
                    <Button 
                        onClick={() => handleSearchSubmit('internal')}
                        className="flex-1 bg-gradient-to-r from-blue-500 to-cyan-600 hover:from-blue-600 hover:to-cyan-700 text-white shadow-lg"
                    >
                        <Zap className="w-5 h-5 mr-2" />
                        Global Search
                    </Button>
                    <Button 
                        onClick={() => handleSearchSubmit('ai')}
                        className="flex-1 bg-gradient-to-r from-purple-500 to-pink-600 hover:from-purple-600 hover:to-pink-700 text-white shadow-lg"
                    >
                        <Sparkles className="w-5 h-5 mr-2" />
                        AI Search
                    </Button>
                </div>

                {/* Debug Accordion */}
                {searchMode === 'ai' && !isLoading && submittedQuery && Object.keys(debugInfo).length > 0 && (
                    <Accordion type="single" collapsible className="mb-8 max-w-2xl">
                        <AccordionItem value="debug" className="border-yellow-500/30 bg-yellow-900/10 rounded-lg px-4">
                            <AccordionTrigger className="text-yellow-300 hover:text-yellow-200">
                                <div className="flex items-center gap-2">
                                    <Bug className="w-5 h-5" />
                                    <span>🔍 Debug - Why {totalResults} results?</span>
                                </div>
                            </AccordionTrigger>
                            <AccordionContent className="text-gray-300 space-y-3 pt-4">
                                <div className="bg-gray-800/50 p-4 rounded-lg space-y-3 text-sm">
                                    {debugInfo.originalQuery && (
                                        <div className="bg-blue-900/20 border border-blue-500/30 p-3 rounded">
                                            <h4 className="text-blue-400 font-semibold mb-1">🔎 Your Search</h4>
                                            <p className="text-white text-base">"{debugInfo.originalQuery}"</p>
                                            {debugInfo.contentType && debugInfo.contentType !== 'all' && (
                                                <p className="text-gray-400 text-sm mt-1">Content Type Filter: <span className="font-bold">{debugInfo.contentType}s</span></p>
                                            )}
                                        </div>
                                    )}
                                    
                                    {debugInfo.aiUnderstanding && (
                                        <div>
                                            <h4 className="text-yellow-400 font-semibold mb-1">🤖 AI Understanding</h4>
                                            <p className="text-white text-base">"{debugInfo.aiUnderstanding}"</p>
                                            {debugInfo.aiFoundMovies !== undefined && debugInfo.aiFoundSeries !== undefined && (
                                                <p className="mt-2">AI suggested: <span className="font-bold">{debugInfo.aiFoundMovies} movies</span>, <span className="font-bold">{debugInfo.aiFoundSeries} series</span></p>
                                            )}
                                            {debugInfo.aiMovieTitles && debugInfo.aiMovieTitles.length > 0 && (
                                                <div className="mt-2">
                                                    <p className="text-gray-400 mb-1">AI suggested movie titles:</p>
                                                    <ul className="text-xs space-y-1 bg-gray-900/50 p-2 rounded">
                                                        {debugInfo.aiMovieTitles.map((title, i) => (
                                                            <li key={i}>• {title}</li>
                                                        ))}
                                                    </ul>
                                                </div>
                                            )}
                                        </div>
                                    )}
                                    
                                    {debugInfo.libraryMoviesCount !== undefined && (
                                        <div>
                                            <h4 className="text-yellow-400 font-semibold mb-1">📚 Your Library</h4>
                                            <p>Movies: <span className="text-white font-bold">{debugInfo.libraryMoviesCount}</span></p>
                                            <p>Series: <span className="text-white font-bold">{debugInfo.librarySeriesCount}</span></p>
                                            {debugInfo.sampleMovies && debugInfo.sampleMovies.length > 0 && (
                                                <div className="mt-2">
                                                    <p className="text-gray-400 mb-1">Sample library titles:</p>
                                                    <ul className="text-xs space-y-1 bg-gray-900/50 p-2 rounded">
                                                        {debugInfo.sampleMovies.slice(0, 10).map((title, i) => (
                                                            <li key={i}>• {title}</li>
                                                        ))}
                                                    </ul>
                                                </div>
                                            )}
                                            {debugInfo.movieCandidatesCount !== undefined && (
                                                <div className="mt-2">
                                                    <h4 className="text-yellow-400 font-semibold mb-1">🔎 Fuzzy Candidates</h4>
                                                    <p>Movies: <span className="font-bold">{debugInfo.movieCandidatesCount}</span></p>
                                                    <p>Series: <span className="font-bold">{debugInfo.seriesCandidatesCount}</span></p>
                                                    {debugInfo.sampleCandidates && debugInfo.sampleCandidates.length > 0 && (
                                                        <div className="mt-2">
                                                            <p className="text-gray-400 mb-1">Sample fuzzy candidates:</p>
                                                            <ul className="text-xs space-y-1 bg-gray-900/50 p-2 rounded">
                                                                {debugInfo.sampleCandidates.slice(0, 10).map((title, i) => (
                                                                    <li key={i}>• {title}</li>
                                                                ))}
                                                            </ul>
                                                        </div>
                                                    )}
                                                </div>
                                            )}
                                        </div>
                                    )}
                                    
                                    {debugInfo.finalMoviesCount !== undefined && (
                                        <div>
                                            <h4 className={`font-semibold mb-1 ${debugInfo.finalMoviesCount > 0 ? 'text-green-400' : 'text-red-400'}`}>
                                                {debugInfo.finalMoviesCount > 0 ? '✅ Final Matches Found in Library!' : '❌ No Final Matches in Library'}
                                            </h4>
                                            <p>Movies: <span className="text-white font-bold">{debugInfo.finalMoviesCount}</span></p>
                                            <p>Series: <span className="text-white font-bold">{debugInfo.finalSeriesCount}</span></p>
                                            {debugInfo.finalMovieTitles && debugInfo.finalMovieTitles.length > 0 && (
                                                <div className="mt-2">
                                                    <p className="text-green-400 mb-1">Matched movies:</p>
                                                    <ul className="text-xs space-y-1 text-green-300 bg-green-900/20 p-2 rounded">
                                                        {debugInfo.finalMovieTitles.map((title, i) => (
                                                            <li key={i}>✓ {title}</li>
                                                        ))}
                                                    </ul>
                                                </div>
                                            )}
                                        </div>
                                    )}
                                    
                                    {totalResults === 0 && debugInfo.aiFoundMovies > 0 && debugInfo.finalMoviesCount === 0 && (
                                        <div className="bg-red-900/20 border border-red-500/30 p-3 rounded mt-2">
                                            <h4 className="text-red-400 font-semibold mb-2">💡 Why no results?</h4>
                                            <div className="text-sm space-y-2">
                                                <p>✅ AI suggested <span className="font-bold">{debugInfo.aiFoundMovies} movies</span> matching "{debugInfo.aiUnderstanding}"</p>
                                                <p className="text-red-300">❌ But none of these suggested titles could be found in your library.</p>
                                                <p className="text-gray-400 mt-3 pt-3 border-t border-gray-700">This means your library doesn't contain the specific titles AI suggested. Check the "AI suggested movie titles" list above to see what AI thought matched.</p>
                                            </div>
                                        </div>
                                    )}
                                    
                                    {totalResults === 0 && debugInfo.aiFoundMovies === 0 && (
                                        <div className="bg-red-900/20 border border-red-500/30 p-3 rounded mt-2">
                                            <h4 className="text-red-400 font-semibold mb-2">💡 Why no results?</h4>
                                            <p className="text-sm">AI could not suggest any content matching "{debugInfo.originalQuery}" from your library titles. Try a different search term or use Global Search instead.</p>
                                        </div>
                                    )}
                                </div>
                            </AccordionContent>
                        </AccordionItem>
                    </Accordion>
                )}

                {isLoading && (
                    <div className="flex flex-col items-center gap-3 text-purple-300">
                        <Loader2 className="w-6 h-6 animate-spin" />
                        <span className="text-lg">{loadingStatus || 'Searching...'}</span>
                    </div>
                )}

                {isError && (
                    <div className="bg-red-900/20 border border-red-500/30 text-red-400 p-4 rounded-lg flex items-start gap-3">
                        <AlertCircle className="w-5 h-5 mt-1" />
                        <div>
                            <h3 className="font-semibold text-white">Search Failed</h3>
                            <p>The search encountered an error. Please try again.</p>
                            <p className="text-xs mt-2 opacity-70">Error: {error?.message || 'An unknown error occurred.'}</p>
                        </div>
                    </div>
                )}

                {!isLoading && !isError && searchResults && (
                    <div className="space-y-8">
                        {searchResults.searchType === 'internal' && (
                            <div className="bg-blue-500/10 border border-blue-500/30 p-4 rounded-lg">
                                <p className="text-blue-200 text-lg">
                                    Found {totalResults} results in your library for: <span className="font-semibold">{submittedQuery}</span>
                                </p>
                                <p className="text-blue-300 text-sm mt-1">
                                    Simple keyword search
                                </p>
                            </div>
                        )}
                        
                        {/* The new AI search returns searchType 'ai' and aiUnderstanding */}
                        {searchResults.searchType === 'ai' && searchResults.aiUnderstanding && (
                            <div className="bg-purple-500/10 border border-purple-500/30 p-4 rounded-lg">
                                <p className="text-purple-200 text-lg">
                                    AI understood your search as: <span className="font-bold">"{searchResults.aiUnderstanding}"</span>
                                </p>
                                <p className="text-purple-300 text-sm mt-1">
                                    Found {totalResults} matching titles in your library using AI.
                                </p>
                            </div>
                        )}
                        
                        {/* These blocks might not be triggered by the new AI search as it returns 'ai' type only */}
                        {searchResults.searchType === 'actor' && searchResults.actorName && (
                            <div className="bg-purple-500/10 border border-purple-500/30 p-4 rounded-lg">
                                <p className="text-purple-200 text-lg">
                                    Found {totalResults} titles featuring <span className="font-bold">{searchResults.actorName}</span>
                                </p>
                            </div>
                        )}
                        
                        {searchResults.searchType === 'topic' && searchResults.topics && (
                            <div className="bg-cyan-500/10 border border-cyan-500/30 p-4 rounded-lg">
                                <p className="text-cyan-200 text-lg">
                                    Found {totalResults} titles related to <span className="font-bold">{searchResults.topics.join(', ')}</span>
                                </p>
                                <p className="text-cyan-300 text-sm mt-1">
                                    AI-powered topic search
                                </p>
                            </div>
                        )}

                        {searchResults.searchType === 'keyword' && searchResults.keywords && (
                            <div className="bg-blue-500/10 border border-blue-500/30 p-4 rounded-lg">
                                <p className="text-blue-200 text-sm">
                                    Searched for: <span className="font-semibold">{searchResults.keywords.join(', ')}</span>
                                </p>
                                <p className="text-blue-300 text-sm mt-1">
                                    Found {totalResults} results
                                </p>
                            </div>
                        )}
                        
                        {totalResults === 0 && (
                            <div className="text-center py-16">
                                <Search className="w-16 h-16 text-gray-500 mx-auto mb-4" />
                                <h3 className="text-xl font-semibold text-white mb-2">No Results Found</h3>
                                <p className="text-gray-400">Try using AI Search for broader results or different keywords.</p>
                            </div>
                        )}

                        {/* On Live TV Now Section */}
                        {searchResults.liveChannels && searchResults.liveChannels.length > 0 && (
                            <div>
                                <h2 className="text-2xl font-bold text-white mb-4 flex items-center gap-2">
                                    <Tv className="text-green-400" />
                                    On Live TV Now ({searchResults.liveChannels.length})
                                </h2>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-8">
                                {searchResults.liveChannels.map((channel, i) => {
                                    const streamUrl = constructStreamUrl(playlist.host, playlist.username, playlist.password, channel.stream_id);
                                    return (
                                        <Link key={i} to={createPageUrl(`Guide?playlistId=${playlistId}&channelUrl=${encodeURIComponent(streamUrl)}&channelName=${encodeURIComponent(channel.name)}&categoryId=${channel.category_id}&categoryName=Live&streamId=${channel.stream_id}`)}>
                                            <Card className="bg-gradient-to-r from-green-900/20 to-gray-800/50 border-green-500/30 hover:border-green-500/60">
                                                <CardContent className="p-4">
                                                    <div className="flex items-start gap-4">
                                                        <div className="w-16 h-16 bg-gray-900 rounded-md flex items-center justify-center overflow-hidden flex-shrink-0">
                                                            {channel.stream_icon ? <img src={channel.stream_icon} alt={channel.name} className="w-full h-full object-contain"/> : <Tv className="w-8 h-8 text-green-400"/>}
                                                        </div>
                                                        <div className="flex-1 min-w-0">
                                                            <div className="flex items-center gap-2 mb-1">
                                                                <span className="px-2 py-0.5 bg-red-500 text-white text-xs font-bold rounded">LIVE NOW</span>
                                                                <p className="text-sm text-gray-400">{channel.name}</p>
                                                            </div>
                                                            {channel.currentProgram ? (
                                                                <>
                                                                    <p className="text-white font-semibold text-lg truncate">{channel.currentProgram.title}</p>
                                                                    <p className="text-gray-400 text-sm line-clamp-2 mt-1">{channel.currentProgram.desc}</p>
                                                                </>
                                                            ) : (
                                                                <p className="text-white font-semibold text-lg truncate">{channel.name}</p>
                                                            )}
                                                        </div>
                                                    </div>
                                                </CardContent>
                                            </Card>
                                        </Link>
                                    )
                                })}
                                </div>
                            </div>
                        )}
                        
                        {/* Movies Section */}
                        {searchResults.movies && searchResults.movies.length > 0 && (
                            <div>
                                <h2 className="text-2xl font-bold text-white mb-4 flex items-center gap-2"><Film />Movies ({searchResults.movies.length})</h2>
                                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                                {searchResults.movies.map((movie, i) => (
                                    <Link key={i} to={createPageUrl(`MovieInfo?playlistId=${playlistId}&vodId=${movie.stream_id}`)}>
                                        <Card className="bg-gray-800/50 hover:border-purple-500/60"><CardContent className="p-2"><div className="aspect-[2/3] bg-gray-900 mb-2 rounded overflow-hidden"><img src={movie.stream_icon} alt={movie.name} className="w-full h-full object-cover"/></div><p className="text-white text-sm truncate">{movie.name}</p></CardContent></Card>
                                    </Link>
                                ))}
                                </div>
                            </div>
                        )}

                        {/* Series Section */}
                        {searchResults.series && searchResults.series.length > 0 && (
                            <div>
                                <h2 className="text-2xl font-bold text-white mb-4 flex items-center gap-2"><Clapperboard />Series ({searchResults.series.length})</h2>
                                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                                {searchResults.series.map((s, i) => (
                                    <Link key={i} to={createPageUrl(`SeriesDetails?playlistId=${playlistId}&seriesId=${s.series_id}`)}>
                                        <Card className="bg-gray-800/50 hover:border-purple-500/60"><CardContent className="p-2"><div className="aspect-[2/3] bg-gray-900 mb-2 rounded overflow-hidden"><img src={s.cover} alt={s.name} className="w-full h-full object-cover"/></div><p className="text-white text-sm truncate">{s.name}</p></CardContent></Card>
                                    </Link>
                                ))}
                                </div>
                            </div>
                        )}
                    </div>
                )}
                
                {!isLoading && !submittedQuery && (
                    <div className="text-center py-16">
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-8 max-w-4xl mx-auto mb-8">
                            <Card className="bg-gradient-to-br from-blue-500/10 to-cyan-500/10 border-blue-500/30">
                                <CardContent className="p-8">
                                    <Zap className="w-16 h-16 text-blue-400 mx-auto mb-4" />
                                    <h3 className="text-xl font-semibold text-white mb-2">Global Search</h3>
                                    <p className="text-gray-400">
                                        Fast keyword search across your entire library. Search movies, series, and live TV by title.
                                    </p>
                                </CardContent>
                            </Card>
                            
                            <Card className="bg-gradient-to-br from-purple-500/10 to-pink-500/10 border-purple-500/30">
                                <CardContent className="p-8">
                                    <Sparkles className="w-16 h-16 text-purple-400 mx-auto mb-4" />
                                    <h3 className="text-xl font-semibold text-white mb-2">AI Search</h3>
                                    <p className="text-gray-400">
                                        Intelligent search using AI. Describe what you want to watch or search for actors, topics, and franchises.
                                    </p>
                                </CardContent>
                            </Card>
                        </div>
                        <p className="text-gray-500 text-sm">
                            Try: "The Matrix", "Tom Hanks movies", "movies about sharks", or "70's sitcoms"
                        </p>
                    </div>
                )}
            </div>
        </div>
    );
}