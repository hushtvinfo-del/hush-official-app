
import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { base44 } from '@/api/base44Client';
import { Link, useNavigate } from 'react-router-dom';
import { createPageUrl } from '@/utils';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ArrowLeft, Search, Film, Clapperboard, Loader2, Sparkles, AlertCircle, Bug } from 'lucide-react';
import { motion } from 'framer-motion';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";

const getPlaylistFromLocal = (playlistId) => {
    try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        return localPlaylists.find(p => p.id === playlistId);
    } catch(e) { return null; }
};

const searchWithAI = async (playlist, query, setStatus, setDebugInfo) => {
    if (!playlist || !query) return { movies: [], series: [] };

    setStatus('Loading your library...');
    setDebugInfo(prev => ({ ...prev, step: 'Loading library...' }));
    
    console.log('🔍 Starting AI search for:', query);
    console.log('📦 Playlist:', playlist.name);

    // Fetch entire library
    const [allVodRes, allSeriesRes] = await Promise.all([
        base44.functions.invoke('xtreamProxy', { 
            host: playlist.host, 
            username: playlist.username, 
            password: playlist.password, 
            params: { action: 'get_vod_streams' } 
        }),
        base44.functions.invoke('xtreamProxy', { 
            host: playlist.host, 
            username: playlist.username, 
            password: playlist.password, 
            params: { action: 'get_series' } 
        })
    ]);
    
    const allMovies = (allVodRes.data || []).map(m => ({ 
        id: m.stream_id, 
        name: m.name, 
        type: 'movie',
        cover: m.stream_icon,
        rating: m.rating 
    }));
    
    const allSeries = (allSeriesRes.data || []).map(s => ({ 
        id: s.series_id, 
        name: s.name, 
        type: 'series',
        cover: s.cover,
        rating: s.rating 
    }));
    
    const contentList = [...allMovies, ...allSeries];
    
    console.log(`✅ Library loaded: ${allMovies.length} movies, ${allSeries.length} series`);
    console.log(`📚 Total items: ${contentList.length}`);
    
    // Show some sample titles
    console.log('📋 Sample movie titles:', allMovies.slice(0, 5).map(m => m.name));
    console.log('📋 Sample series titles:', allSeries.slice(0, 5).map(s => s.name));

    setDebugInfo({
        step: 'Library loaded',
        moviesCount: allMovies.length,
        seriesCount: allSeries.length,
        totalCount: contentList.length,
        sampleMovies: allMovies.slice(0, 10).map(m => m.name),
        sampleSeries: allSeries.slice(0, 10).map(s => s.name)
    });
    
    setStatus(`Analyzing ${contentList.length} titles with AI...`);

    console.log('🤖 Calling AI function...');
    
    // Call NEW AI function
    const aiRes = await base44.functions.invoke('aiSearch', {
        query: query,
        contentList: contentList
    });

    console.log('✅ AI response received:', aiRes.data);

    const selectedMovies = (aiRes.data.movies || []);
    const selectedSeries = (aiRes.data.series || []);

    console.log(`🎯 Final results: ${selectedMovies.length} movies, ${selectedSeries.length} series`);

    setDebugInfo(prev => ({
        ...prev,
        step: 'Search complete',
        aiReturnedMovies: selectedMovies.length,
        aiReturnedSeries: selectedSeries.length,
        movieTitles: selectedMovies.map(m => m.name),
        seriesTitles: selectedSeries.map(s => s.name)
    }));
    
    setStatus('');
    return { 
        movies: selectedMovies, 
        series: selectedSeries
    };
};

export default function AiSearch() {
    const navigate = useNavigate();
    const urlParams = new URLSearchParams(window.location.search);
    const playlistId = urlParams.get('playlistId');
    const initialQuery = urlParams.get('q') || '';
    const [searchQuery, setSearchQuery] = useState(initialQuery);
    const [submittedQuery, setSubmittedQuery] = useState(initialQuery);
    const [loadingStatus, setLoadingStatus] = useState('');
    const [debugInfo, setDebugInfo] = useState({});

    const { data: playlist } = useQuery({
      queryKey: ['playlist', playlistId],
      queryFn: () => getPlaylistFromLocal(playlistId),
      enabled: !!playlistId
    });

    const { data: searchResults, isLoading, isError, error } = useQuery({
        queryKey: ['aiSearch', playlistId, submittedQuery],
        queryFn: () => searchWithAI(playlist, submittedQuery, setLoadingStatus, setDebugInfo),
        enabled: !!playlistId && !!submittedQuery,
        staleTime: 5 * 60 * 1000,
        retry: 0,
        refetchOnWindowFocus: false,
    });
    
    const handleSearchSubmit = (e) => {
        e.preventDefault();
        if (searchQuery.trim()) {
            setDebugInfo({}); // Reset debug info for new search
            setSubmittedQuery(searchQuery.trim());
            navigate(createPageUrl(`AiSearch?playlistId=${playlistId}&q=${encodeURIComponent(searchQuery.trim())}`), { replace: true });
        }
    };

    const totalResults = (searchResults?.movies?.length || 0) + (searchResults?.series?.length || 0);

    return (
        <div className="min-h-screen p-4 md:p-8">
            <div className="max-w-7xl mx-auto">
                <Button variant="ghost" onClick={() => navigate(-1)} className="mb-6 text-purple-300 hover:text-white hover:bg-purple-500/20">
                    <ArrowLeft className="w-4 h-4 mr-2" />
                    Back
                </Button>
                
                <h1 className="text-3xl md:text-4xl font-bold text-white mb-2 flex items-center gap-3">
                    <Sparkles className="text-purple-400" />
                    AI-Powered Search
                </h1>
                <p className="text-purple-300 mb-2">Search for movies, series, actors, genres, or describe what you want to watch</p>
                <p className="text-yellow-400 text-sm mb-6">⚠️ Currently searching: Movies & Series only (Live TV not included)</p>
                
                <form onSubmit={handleSearchSubmit} className="relative max-w-2xl mb-8">
                    <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
                    <Input
                        type="text"
                        placeholder='Try: "Narcos", "all Terminator movies", "Tom Hanks movies", "action movies from the 90s"'
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="pl-10 text-lg bg-gray-800/50 border-purple-500/30 text-white placeholder:text-gray-500 focus:border-purple-500"
                    />
                </form>

                {isLoading && (
                    <div className="flex flex-col items-center gap-3 text-purple-300 py-12">
                        <Loader2 className="w-12 h-12 animate-spin" />
                        <span className="text-xl font-semibold">{loadingStatus || 'Searching...'}</span>
                        <p className="text-sm text-gray-400">Check browser console (F12) for detailed logs...</p>
                    </div>
                )}

                {isError && (
                    <div className="bg-red-900/20 border border-red-500/30 text-red-400 p-6 rounded-lg flex items-start gap-3">
                        <AlertCircle className="w-6 h-6 mt-1" />
                        <div>
                            <h3 className="font-semibold text-white text-lg mb-2">Search Failed</h3>
                            <p>The AI search encountered an error. Please try again.</p>
                            <p className="text-xs mt-2 opacity-70">Error: {error?.message || 'Unknown error'}</p>
                        </div>
                    </div>
                )}

                {/* Debug Information */}
                {!isLoading && submittedQuery && Object.keys(debugInfo).length > 0 && (
                    <Accordion type="single" collapsible className="mb-8">
                        <AccordionItem value="debug" className="border border-yellow-500/30 bg-yellow-900/10 rounded-lg px-4">
                            <AccordionTrigger className="text-yellow-300 hover:text-yellow-200">
                                <div className="flex items-center gap-2">
                                    <Bug className="w-5 h-5" />
                                    <span>🔍 Debug Information - Why did I get {totalResults} results?</span>
                                </div>
                            </AccordionTrigger>
                            <AccordionContent className="text-gray-300 space-y-4 pt-4">
                                <div className="bg-gray-800/50 p-4 rounded-lg space-y-3">
                                    <div>
                                        <h4 className="text-yellow-400 font-semibold mb-1">📊 Library Stats</h4>
                                        <p>Total Items: <span className="text-white font-bold">{debugInfo.totalCount || 0}</span></p>
                                        <p>Movies: <span className="text-white font-bold">{debugInfo.moviesCount || 0}</span></p>
                                        <p>Series: <span className="text-white font-bold">{debugInfo.seriesCount || 0}</span></p>
                                    </div>

                                    {debugInfo.sampleMovies && debugInfo.sampleMovies.length > 0 && (
                                        <div>
                                            <h4 className="text-yellow-400 font-semibold mb-1">🎬 Sample Movies (first 10)</h4>
                                            <ul className="text-sm space-y-1">
                                                {debugInfo.sampleMovies.map((title, i) => (
                                                    <li key={i}>• {title}</li>
                                                ))}
                                            </ul>
                                        </div>
                                    )}

                                    {debugInfo.sampleSeries && debugInfo.sampleSeries.length > 0 && (
                                        <div>
                                            <h4 className="text-yellow-400 font-semibold mb-1">📺 Sample Series (first 10)</h4>
                                            <ul className="text-sm space-y-1">
                                                {debugInfo.sampleSeries.map((title, i) => (
                                                    <li key={i}>• {title}</li>
                                                ))}
                                            </ul>
                                        </div>
                                    )}

                                    <div>
                                        <h4 className="text-yellow-400 font-semibold mb-1">🤖 AI Results</h4>
                                        <p>Movies Found: <span className="text-white font-bold">{debugInfo.aiReturnedMovies || 0}</span></p>
                                        <p>Series Found: <span className="text-white font-bold">{debugInfo.aiReturnedSeries || 0}</span></p>
                                    </div>

                                    {debugInfo.movieTitles && debugInfo.movieTitles.length > 0 && (
                                        <div>
                                            <h4 className="text-green-400 font-semibold mb-1">✅ Matched Movies</h4>
                                            <ul className="text-sm space-y-1">
                                                {debugInfo.movieTitles.map((title, i) => (
                                                    <li key={i} className="text-green-300">• {title}</li>
                                                ))}
                                            </ul>
                                        </div>
                                    )}

                                    {debugInfo.seriesTitles && debugInfo.seriesTitles.length > 0 && (
                                        <div>
                                            <h4 className="text-green-400 font-semibold mb-1">✅ Matched Series</h4>
                                            <ul className="text-sm space-y-1">
                                                {debugInfo.seriesTitles.map((title, i) => (
                                                    <li key={i} className="text-green-300">• {title}</li>
                                                ))}
                                            </ul>
                                        </div>
                                    )}

                                    {totalResults === 0 && debugInfo.totalCount > 0 && (
                                        <div className="bg-red-900/20 border border-red-500/30 p-3 rounded">
                                            <h4 className="text-red-400 font-semibold mb-1">❌ No Matches Found</h4>
                                            <p className="text-sm">The AI couldn't find any titles matching "{submittedQuery}" in your {debugInfo.totalCount} items.</p>
                                            <p className="text-sm mt-2 text-gray-400">Try:</p>
                                            <ul className="text-sm text-gray-400 mt-1">
                                                <li>• Using exact movie/series names from the sample lists above</li>
                                                <li>• Searching for a different title</li>
                                                <li>• Using more general terms like "action movies"</li>
                                            </ul>
                                        </div>
                                    )}
                                </div>
                            </AccordionContent>
                        </AccordionItem>
                    </Accordion>
                )}

                {!isLoading && !isError && searchResults && (
                    <div className="space-y-8">
                        {totalResults > 0 ? (
                            <>
                                <div className="bg-purple-500/10 border border-purple-500/30 p-4 rounded-lg">
                                    <p className="text-purple-200 text-lg">
                                        Found <span className="font-bold">{totalResults} results</span> for "{submittedQuery}"
                                    </p>
                                </div>

                                {/* Movies Section */}
                                {searchResults.movies && searchResults.movies.length > 0 && (
                                    <div>
                                        <h2 className="text-2xl font-bold text-white mb-4 flex items-center gap-2">
                                            <Film className="text-purple-400" />
                                            Movies ({searchResults.movies.length})
                                        </h2>
                                        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                                            {searchResults.movies.map((movie, i) => (
                                                <Link key={movie.id} to={createPageUrl(`MovieInfo?playlistId=${playlistId}&vodId=${movie.id}`)}>
                                                    <motion.div
                                                        initial={{ opacity: 0, y: 20 }}
                                                        animate={{ opacity: 1, y: 0 }}
                                                        transition={{ delay: i * 0.02 }}
                                                    >
                                                        <Card className="bg-gray-800/50 hover:border-purple-500/60 transition-all group">
                                                            <CardContent className="p-3">
                                                                <div className="aspect-[2/3] bg-gray-900 mb-2 rounded overflow-hidden relative">
                                                                    {movie.cover ? (
                                                                        <img 
                                                                            src={movie.cover} 
                                                                            alt={movie.name} 
                                                                            className="w-full h-full object-cover"
                                                                            loading="lazy"
                                                                        />
                                                                    ) : (
                                                                        <div className="w-full h-full flex items-center justify-center">
                                                                            <Film className="w-12 h-12 text-purple-400" />
                                                                        </div>
                                                                    )}
                                                                    <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity" />
                                                                </div>
                                                                <p className="text-white text-sm truncate font-medium">{movie.name}</p>
                                                            </CardContent>
                                                        </Card>
                                                    </motion.div>
                                                </Link>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                {/* Series Section */}
                                {searchResults.series && searchResults.series.length > 0 && (
                                    <div>
                                        <h2 className="text-2xl font-bold text-white mb-4 flex items-center gap-2">
                                            <Clapperboard className="text-purple-400" />
                                            Series ({searchResults.series.length})
                                        </h2>
                                        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                                            {searchResults.series.map((series, i) => (
                                                <Link key={series.id} to={createPageUrl(`SeriesDetails?playlistId=${playlistId}&seriesId=${series.id}`)}>
                                                    <motion.div
                                                        initial={{ opacity: 0, y: 20 }}
                                                        animate={{ opacity: 1, y: 0 }}
                                                        transition={{ delay: i * 0.02 }}
                                                    >
                                                        <Card className="bg-gray-800/50 hover:border-purple-500/60 transition-all group">
                                                            <CardContent className="p-3">
                                                                <div className="aspect-[2/3] bg-gray-900 mb-2 rounded overflow-hidden relative">
                                                                    {series.cover ? (
                                                                        <img 
                                                                            src={series.cover} 
                                                                            alt={series.name} 
                                                                            className="w-full h-full object-cover"
                                                                            loading="lazy"
                                                                        />
                                                                    ) : (
                                                                        <div className="w-full h-full flex items-center justify-center">
                                                                            <Clapperboard className="w-12 h-12 text-purple-400" />
                                                                        </div>
                                                                    )}
                                                                    <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity" />
                                                                </div>
                                                                <p className="text-white text-sm truncate font-medium">{series.name}</p>
                                                            </CardContent>
                                                        </Card>
                                                    </motion.div>
                                                </Link>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </>
                        ) : (
                            <div className="text-center py-12">
                                <AlertCircle className="w-16 h-16 text-yellow-400 mx-auto mb-4" />
                                <h3 className="text-2xl font-semibold text-white mb-2">No Results Found</h3>
                                <p className="text-gray-400 max-w-md mx-auto">
                                    Couldn't find any matches for "{submittedQuery}" in your library. Check the debug info above for details.
                                </p>
                            </div>
                        )}
                    </div>
                )}

                {!isLoading && !submittedQuery && (
                    <div className="text-center py-16">
                        <Sparkles className="w-20 h-20 text-purple-400 mx-auto mb-6" />
                        <h3 className="text-2xl font-semibold text-white mb-3">Intelligent Content Discovery</h3>
                        <p className="text-gray-400 max-w-2xl mx-auto text-lg">
                            Powered by Claude AI, search naturally for anything you want to watch
                        </p>
                        <div className="mt-8 grid grid-cols-1 md:grid-cols-2 gap-4 max-w-3xl mx-auto text-left">
                            <div className="bg-purple-500/10 border border-purple-500/30 p-4 rounded-lg">
                                <p className="text-purple-300 font-semibold mb-2">🎬 Specific Titles</p>
                                <p className="text-gray-400 text-sm">"Narcos", "Breaking Bad", "The Matrix"</p>
                            </div>
                            <div className="bg-purple-500/10 border border-purple-500/30 p-4 rounded-lg">
                                <p className="text-purple-300 font-semibold mb-2">🎭 By Actor</p>
                                <p className="text-gray-400 text-sm">"Tom Cruise movies", "shows with Bryan Cranston"</p>
                            </div>
                            <div className="bg-purple-500/10 border border-purple-500/30 p-4 rounded-lg">
                                <p className="text-purple-300 font-semibold mb-2">🎯 Collections</p>
                                <p className="text-gray-400 text-sm">"All Terminator movies", "Marvel films"</p>
                            </div>
                            <div className="bg-purple-500/10 border border-purple-500/30 p-4 rounded-lg">
                                <p className="text-purple-300 font-semibold mb-2">🎨 By Theme</p>
                                <p className="text-gray-400 text-sm">"90s action movies", "sci-fi series"</p>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
