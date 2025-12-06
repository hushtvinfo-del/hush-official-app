import React from "react";
import { Star } from "lucide-react";

export default function RatingBadge({ rating, size = "sm" }) {
    if (!rating) return null;
    
    // Convert rating to number if it's a string
    const numericRating = typeof rating === 'string' ? parseFloat(rating) : rating;
    
    // Return null if rating is not a valid number
    if (isNaN(numericRating)) return null;
    
    const sizeClasses = {
        sm: "text-xs px-2 py-1",
        md: "text-sm px-3 py-1.5",
        lg: "text-base px-4 py-2"
    };
    
    const getRatingColor = (score) => {
        if (score >= 7.5) return "bg-green-600";
        if (score >= 6.0) return "bg-yellow-600";
        return "bg-red-600";
    };
    
    return (
        <div className={`${getRatingColor(numericRating)} text-white rounded-md font-bold flex items-center gap-1 ${sizeClasses[size]} shadow-lg`}>
            <Star className="w-3 h-3 fill-white" />
            <span>{numericRating.toFixed(1)}</span>
        </div>
    );
}