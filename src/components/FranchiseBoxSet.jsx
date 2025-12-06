import React from "react";
import { useQuery } from "@tanstack/react-query";
import { base44 } from "@/api/base44Client";
import { Link } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ScrollArea, ScrollBar } from "@/components/ui/scroll-area";
import { ChevronLeft, ChevronRight, Film, Loader2, Play } from "lucide-react";
import { motion } from "framer-motion";

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

const calculateMatchScore = (libraryTitle, tmdbTitle) => {
    const norm1 = normalizeTitle(libraryTitle);
    const norm2 = normalizeTitle(tmdbTitle);
    
    if (!norm1 || !norm2) return 0;
    
    // Exact match
    if (norm1 === norm2) return 100;
    
    // Check if one contains the other (for sequels like "Matrix" vs "Matrix Reloaded")
    // But require the extra part to be significant
    if (norm1.startsWith(norm2) || norm2.startsWith(norm1)) {
        const longer = norm1.length > norm2.length ? norm1 : norm2;
        const shorter = norm1.length > norm2.length ? norm2 : norm1;
        const extraPart = longer.substring(shorter.length).trim();
        
        // If there's a substantial extra part (like "reloaded", "2", "II"), it's likely a sequel
        if (extraPart.length > 0) {
            // Check if the extra part is just a number or roman numeral (strong sequel indicator)
            if (/^(2|3|4|5|ii|iii|iv|v|vi|part\s*\d+|chapter\s*\d+)$/i.test(extraPart)) {
                return 95; // High score for numbered sequels
            }
            // Otherwise it's a sequel with a subtitle
            return 90;
        }
        // If no extra part, exact match
        return 100;
    }
    
    // Word-based matching with stricter criteria
    const words1 = norm1.split(' ').filter(w => w.length > 2);
    const words2 = norm2.split(' ').filter(w => w.length > 2);
    
    if (words1.length >= 2 && words2.length >= 2) {
        const matchingWords = words1.filter(w => words2.includes(w)).length;
        const totalWords = Math.max(words1.length, words2.length);
        const matchRatio = matchingWords / totalWords;
        
        // Require at least 85% word match (stricter than before)
        if (matchRatio >= 0.85) {
            return Math.floor(matchRatio * 80); // Max 68 points for word matching
        }
    }
    
    return 0;
};

export default function FranchiseBoxSet({ playlistId, currentMovieId, currentMovieName, playlist }) {
    const scrollContainerRef = React.useRef(null);

    const { data: franchiseData, isLoading, error: queryError } = useQuery({
        queryKey: ['tmdb-franchise', currentMovieName],
        queryFn: async () => {
            try {
                if (!playlist || !currentMovieName) {
                    return null;
                }

                const libraryResponse = await base44.functions.invoke('xtreamProxy', {
                    host: playlist.host,
                    username: playlist.username,
                    password: playlist.password,
                    params: { action: 'get_vod_streams' }
                });

                const allMovies = libraryResponse.data;

                if (!allMovies || allMovies.length === 0) {
                    return null;
                }

                const tmdbResponse = await base44.functions.invoke('getTmdbDetails', {
                    type: 'movie',
                    name: currentMovieName,
                });
                
                if (!tmdbResponse.data || tmdbResponse.data.error) {
                    return null;
                }

                const tmdbData = tmdbResponse.data;

                if (!tmdbData.belongs_to_collection) {
                    return null;
                }

                const collection = tmdbData.belongs_to_collection;

                const collectionResponse = await base44.functions.invoke('getTmdbCollection', {
                    collection_id: collection.id
                });

                if (!collectionResponse.data || collectionResponse.data.error) {
                    return null;
                }

                const collectionData = collectionResponse.data;

                if (!collectionData.parts || collectionData.parts.length === 0) {
                    return null;
                }

                // Track which library movies have been used
                const usedStreamIds = new Set();
                const franchiseMovies = [];
                
                // For each TMDB movie in the collection
                for (const tmdbMovie of collectionData.parts) {
                    let bestMatch = null;
                    let bestMatchScore = 0;
                    
                    // Find the best matching library movie
                    for (const libraryMovie of allMovies) {
                        const streamId = String(libraryMovie.stream_id);
                        
                        // Skip if it's the current movie or already used
                        if (streamId === String(currentMovieId) || usedStreamIds.has(streamId)) {
                            continue;
                        }
                        
                        const score = calculateMatchScore(libraryMovie.name, tmdbMovie.title);
                        
                        if (score > bestMatchScore) {
                            bestMatch = libraryMovie;
                            bestMatchScore = score;
                        }
                    }
                    
                    // Only add if we found a good match (score >= 85)
                    if (bestMatch && bestMatchScore >= 85) {
                        usedStreamIds.add(String(bestMatch.stream_id));
                        franchiseMovies.push({
                            ...bestMatch,
                            tmdb: tmdbMovie,
                            matchScore: bestMatchScore
                        });
                    }
                }

                if (franchiseMovies.length === 0) {
                    return null;
                }

                // Sort by release date (chronological order)
                franchiseMovies.sort((a, b) => {
                    const dateA = a.tmdb?.release_date || '9999';
                    const dateB = b.tmdb?.release_date || '9999';
                    return dateA.localeCompare(dateB);
                });

                return {
                    franchise_name: collectionData.name,
                    movies: franchiseMovies,
                    poster: collectionData.poster_path ? `https://image.tmdb.org/t/p/w500${collectionData.poster_path}` : null,
                };
            } catch (err) {
                console.error('FranchiseBoxSet error:', err);
                return null;
            }
        },
        enabled: !!playlist && !!currentMovieName && !!currentMovieId,
        staleTime: 1000 * 60 * 60 * 24,
        retry: 0,
        refetchOnWindowFocus: false,
    });

    const scroll = (direction) => {
        if (scrollContainerRef.current) {
            const scrollAmount = 300;
            scrollContainerRef.current.scrollBy({
                left: direction === 'left' ? -scrollAmount : scrollAmount,
                behavior: 'smooth'
            });
        }
    };

    if (isLoading) {
        return (
            <motion.div 
                initial={{ opacity: 0, y: 20 }} 
                animate={{ opacity: 1, y: 0 }} 
                className="mt-6"
            >
                <Card className="bg-gradient-to-r from-orange-900/30 to-amber-900/30 backdrop-blur-xl border-orange-500/30 overflow-hidden">
                    <CardContent className="p-4">
                        <div className="flex items-center justify-center py-8">
                            <Loader2 className="w-6 h-6 animate-spin text-orange-400 mr-2" />
                            <span className="text-white">Searching movie database for box sets...</span>
                        </div>
                    </CardContent>
                </Card>
            </motion.div>
        );
    }

    if (!franchiseData || franchiseData.movies.length === 0) {
        return null;
    }

    return (
        <motion.div 
            initial={{ opacity: 0, y: 20 }} 
            animate={{ opacity: 1, y: 0 }} 
            transition={{ delay: 0.2 }}
            className="mt-6"
        >
            <Card className="bg-gradient-to-r from-orange-900/30 to-amber-900/30 backdrop-blur-xl border-orange-500/30 overflow-hidden">
                <CardContent className="p-4">
                    <div className="flex items-center justify-between mb-3">
                        <div className="flex-1">
                            <h2 className="text-lg font-bold text-white flex items-center gap-2 mb-1">
                                {franchiseData.franchise_name}
                            </h2>
                            <p className="text-xs text-orange-200/80">
                                Watch the complete collection in chronological order
                            </p>
                        </div>
                        <div className="flex gap-1">
                            <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => scroll('left')}
                                className="h-8 w-8 text-orange-400 hover:bg-orange-500/20"
                            >
                                <ChevronLeft className="w-4 h-4" />
                            </Button>
                            <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => scroll('right')}
                                className="h-8 w-8 text-orange-400 hover:bg-orange-500/20"
                            >
                                <ChevronRight className="w-4 h-4" />
                            </Button>
                        </div>
                    </div>
                    
                    <div className="relative">
                        <ScrollArea className="w-full whitespace-nowrap" ref={scrollContainerRef}>
                            <div className="flex gap-3 pb-2">
                                {franchiseData.movies.map((movie, index) => {
                                    const targetUrl = createPageUrl(`MovieInfo?playlistId=${playlistId}&vodId=${movie.stream_id}`);
                                    const releaseYear = movie.tmdb?.release_date?.substring(0, 4) || '';
                                    const poster = movie.tmdb?.poster_path 
                                        ? `https://image.tmdb.org/t/p/w300${movie.tmdb.poster_path}`
                                        : movie.stream_icon;

                                    return (
                                        <div key={`${movie.stream_id}-${index}`} className="relative group flex-shrink-0" style={{ width: '140px' }}>
                                            <Link to={targetUrl}>
                                                <motion.div 
                                                    whileHover={{ scale: 1.05 }} 
                                                    className="h-full"
                                                >
                                                    <div className="bg-gray-900/50 rounded-lg overflow-hidden group-hover:bg-gray-900/70 transition-all shadow-lg hover:shadow-orange-500/20 border border-orange-500/20">
                                                        <div className="aspect-[2/3] bg-gray-900 relative">
                                                            {poster ? (
                                                                <img 
                                                                    src={poster} 
                                                                    alt={movie.tmdb?.title || movie.name} 
                                                                    className="w-full h-full object-cover"
                                                                    loading="lazy"
                                                                    onError={(e) => { 
                                                                        e.target.onerror = null; 
                                                                        e.target.style.display='none'; 
                                                                        e.target.nextSibling.style.display='flex';
                                                                    }}
                                                                />
                                                            ) : null}
                                                            <div style={{ display: poster ? 'none' : 'flex' }} className="w-full h-full items-center justify-center absolute inset-0 bg-gradient-to-br from-orange-800 to-amber-800">
                                                                <Film className="w-10 h-10 text-white" />
                                                            </div>
                                                            
                                                            <div className="absolute top-2 left-2 bg-orange-600 text-white text-xs font-bold px-2 py-1 rounded-full shadow-lg">
                                                                #{index + 1}
                                                            </div>
                                                            
                                                            <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                                                                <div className="w-10 h-10 bg-orange-600 rounded-full flex items-center justify-center">
                                                                    <Play className="w-5 h-5 text-white ml-0.5" />
                                                                </div>
                                                            </div>
                                                        </div>
                                                        <div className="p-2 bg-gradient-to-r from-orange-900/50 to-amber-900/50">
                                                            <p className="text-xs font-semibold text-white truncate" title={movie.tmdb?.title || movie.name}>
                                                                {movie.tmdb?.title || movie.name}
                                                            </p>
                                                            {releaseYear && (
                                                                <p className="text-[10px] text-orange-300">
                                                                    {releaseYear}
                                                                </p>
                                                            )}
                                                        </div>
                                                    </div>
                                                </motion.div>
                                            </Link>
                                        </div>
                                    );
                                })}
                            </div>
                            <ScrollBar orientation="horizontal" className="h-2" />
                        </ScrollArea>
                    </div>
                    <div className="flex items-center justify-between mt-3">
                        <p className="text-xs text-orange-300">
                            ✨ {franchiseData.movies.length} {franchiseData.movies.length === 1 ? 'movie' : 'movies'} • Sorted chronologically
                        </p>
                        <p className="text-[10px] text-orange-400/70">
                            Powered by TMDb
                        </p>
                    </div>
                </CardContent>
            </Card>
        </motion.div>
    );
}