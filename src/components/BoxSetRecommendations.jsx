import React from "react";
import { useQuery } from "@tanstack/react-query";
import { base44 } from "@/api/base44Client";
import { Link } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ScrollArea, ScrollBar } from "@/components/ui/scroll-area";
import { ChevronLeft, ChevronRight, Film, Clapperboard, Loader2, Popcorn } from "lucide-react";
import { motion } from "framer-motion";

export default function BoxSetRecommendations({ playlistId, type = "movie" }) {
    const scrollContainerRef = React.useRef(null);

    const { data: boxSets, isLoading } = useQuery({
        queryKey: ['boxSets', type],
        queryFn: async () => {
            if (type === 'movie') {
                // Fetch popular movie collections from TMDB
                const response = await base44.integrations.Core.InvokeLLM({
                    prompt: `List exactly 10 of the most popular movie franchises/box sets that people love to binge watch. 
                    
                    Include classics and modern franchises like:
                    - Harry Potter
                    - Lord of the Rings
                    - Marvel Cinematic Universe
                    - Star Wars
                    - James Bond
                    - Fast and Furious
                    - Mission Impossible
                    - Jurassic Park
                    - The Matrix
                    - Terminator
                    
                    Return ONLY the franchise names, one per line.`,
                    response_json_schema: {
                        type: "object",
                        properties: {
                            franchises: {
                                type: "array",
                                items: { type: "string" },
                                description: "List of popular movie franchises"
                            }
                        },
                        required: ["franchises"]
                    }
                });

                // Fetch details from TMDB for each franchise
                const franchisesWithData = await Promise.all(
                    response.franchises.slice(0, 10).map(async (franchiseName) => {
                        try {
                            const tmdbResponse = await base44.functions.invoke('getTmdbDetails', {
                                type: 'movie',
                                name: franchiseName,
                                year: null
                            });
                            
                            if (tmdbResponse.data && tmdbResponse.data.poster_path) {
                                return {
                                    name: franchiseName,
                                    poster: `https://image.tmdb.org/t/p/w500${tmdbResponse.data.poster_path}`,
                                    backdrop: tmdbResponse.data.backdrop_path ? `https://image.tmdb.org/t/p/w500${tmdbResponse.data.backdrop_path}` : null,
                                    searchQuery: franchiseName
                                };
                            }
                        } catch (error) {
                            console.warn(`Failed to fetch TMDB data for ${franchiseName}:`, error);
                        }
                        return null;
                    })
                );

                return franchisesWithData.filter(Boolean);
            } else {
                // Fetch popular TV series for binge watching
                const response = await base44.integrations.Core.InvokeLLM({
                    prompt: `List exactly 10 of the most popular TV series/box sets that people love to binge watch.
                    
                    Include iconic series like:
                    - Breaking Bad
                    - Game of Thrones
                    - Friends
                    - The Office
                    - Stranger Things
                    - The Walking Dead
                    - Narcos
                    - Peaky Blinders
                    - The Crown
                    - Succession
                    
                    Return ONLY the series names, one per line.`,
                    response_json_schema: {
                        type: "object",
                        properties: {
                            series: {
                                type: "array",
                                items: { type: "string" },
                                description: "List of popular TV series"
                            }
                        },
                        required: ["series"]
                    }
                });

                const seriesWithData = await Promise.all(
                    response.series.slice(0, 10).map(async (seriesName) => {
                        try {
                            const tmdbResponse = await base44.functions.invoke('getTmdbDetails', {
                                type: 'tv',
                                name: seriesName,
                                year: null
                            });
                            
                            if (tmdbResponse.data && tmdbResponse.data.poster_path) {
                                return {
                                    name: seriesName,
                                    poster: `https://image.tmdb.org/t/p/w500${tmdbResponse.data.poster_path}`,
                                    backdrop: tmdbResponse.data.backdrop_path ? `https://image.tmdb.org/t/p/w500${tmdbResponse.data.backdrop_path}` : null,
                                    searchQuery: seriesName
                                };
                            }
                        } catch (error) {
                            console.warn(`Failed to fetch TMDB data for ${seriesName}:`, error);
                        }
                        return null;
                    })
                );

                return seriesWithData.filter(Boolean);
            }
        },
        staleTime: 1000 * 60 * 60 * 24, // Cache for 24 hours
        retry: 1,
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
                className="mb-6"
            >
                <Card className="bg-gradient-to-r from-purple-900/30 to-pink-900/30 backdrop-blur-xl border-purple-500/30 overflow-hidden">
                    <CardContent className="p-4">
                        <div className="flex items-center justify-center py-8">
                            <Loader2 className="w-6 h-6 animate-spin text-purple-400 mr-2" />
                            <span className="text-white">Loading box sets...</span>
                        </div>
                    </CardContent>
                </Card>
            </motion.div>
        );
    }

    if (!boxSets || boxSets.length === 0) {
        return null;
    }

    return (
        <motion.div 
            initial={{ opacity: 0, y: 20 }} 
            animate={{ opacity: 1, y: 0 }} 
            transition={{ delay: 0.1 }}
            className="mb-6"
        >
            <Card className="bg-gradient-to-r from-purple-900/30 to-pink-900/30 backdrop-blur-xl border-purple-500/30 overflow-hidden">
                <CardContent className="p-4">
                    <div className="flex items-center justify-between mb-3">
                        <h2 className="text-lg font-bold text-white flex items-center gap-2">
                            <Popcorn className="w-5 h-5 text-purple-400" />
                            Why not indulge in a box set?
                        </h2>
                        <div className="flex gap-1">
                            <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => scroll('left')}
                                className="h-8 w-8 text-purple-400 hover:bg-purple-500/20"
                            >
                                <ChevronLeft className="w-4 h-4" />
                            </Button>
                            <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => scroll('right')}
                                className="h-8 w-8 text-purple-400 hover:bg-purple-500/20"
                            >
                                <ChevronRight className="w-4 h-4" />
                            </Button>
                        </div>
                    </div>
                    
                    <div className="relative">
                        <ScrollArea className="w-full whitespace-nowrap" ref={scrollContainerRef}>
                            <div className="flex gap-3 pb-2">
                                {boxSets.map((boxSet, index) => {
                                    const searchUrl = createPageUrl(
                                        `GlobalSearch?playlistId=${playlistId}&q=${encodeURIComponent(boxSet.searchQuery)}&mode=ai`
                                    );

                                    return (
                                        <div key={index} className="relative group flex-shrink-0" style={{ width: '140px' }}>
                                            <Link to={searchUrl}>
                                                <motion.div 
                                                    whileHover={{ scale: 1.05 }} 
                                                    className="h-full"
                                                >
                                                    <div className="bg-gray-900/50 rounded-lg overflow-hidden group-hover:bg-gray-900/70 transition-all shadow-lg hover:shadow-purple-500/20">
                                                        <div className="aspect-[2/3] bg-gray-900 relative">
                                                            {boxSet.poster ? (
                                                                <img 
                                                                    src={boxSet.poster} 
                                                                    alt={boxSet.name} 
                                                                    className="w-full h-full object-cover"
                                                                    onError={(e) => {
                                                                        e.target.style.display = 'none';
                                                                        e.target.nextElementSibling.style.display = 'flex';
                                                                    }}
                                                                />
                                                            ) : null}
                                                            <div className={`w-full h-full ${boxSet.poster ? 'hidden' : 'flex'} items-center justify-center absolute inset-0 bg-gradient-to-br from-purple-800 to-pink-800`}>
                                                                {type === 'movie' ? (
                                                                    <Film className="w-10 h-10 text-white" />
                                                                ) : (
                                                                    <Clapperboard className="w-10 h-10 text-white" />
                                                                )}
                                                            </div>
                                                            <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-100" />
                                                            <div className="absolute bottom-0 left-0 right-0 p-2">
                                                                <p className="text-xs font-bold text-white text-center drop-shadow-lg">
                                                                    {boxSet.name}
                                                                </p>
                                                            </div>
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
                    <p className="text-xs text-purple-300 mt-2 text-center">
                        ✨ Tap any box set to search your library
                    </p>
                </CardContent>
            </Card>
        </motion.div>
    );
}