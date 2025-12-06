import React from "react";
import { Star, Popcorn } from "lucide-react";

export default function MultiRatingBadge({ ratings, size = "sm" }) {
    if (!ratings || (!ratings.imdb && !ratings.audience && !ratings.critic)) return null;
    
    const sizeClasses = {
        sm: "text-xs gap-2",
        md: "text-sm gap-3",
        lg: "text-base gap-4"
    };
    
    const iconSize = size === "sm" ? "w-3 h-3" : size === "md" ? "w-4 h-4" : "w-5 h-5";
    
    return (
        <div className={`flex items-center ${sizeClasses[size]} flex-wrap`}>
            {ratings.imdb && (
                <div className="flex items-center gap-1 bg-yellow-500/20 px-2 py-1 rounded-md">
                    <Star className={`${iconSize} text-yellow-400 fill-yellow-400`} />
                    <span className="text-white font-semibold">{ratings.imdb}</span>
                </div>
            )}
            {ratings.audience && (
                <div className="flex items-center gap-1 bg-orange-500/20 px-2 py-1 rounded-md">
                    <Popcorn className={`${iconSize} text-orange-400 fill-orange-400`} />
                    <span className="text-white font-semibold">{ratings.audience}%</span>
                </div>
            )}
            {ratings.critic && (
                <div className="flex items-center gap-1 bg-red-500/20 px-2 py-1 rounded-md">
                    <span className="text-red-400 font-bold text-base">🍅</span>
                    <span className="text-white font-semibold">{ratings.critic}%</span>
                </div>
            )}
        </div>
    );
}