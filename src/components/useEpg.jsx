import { useQuery } from "@tanstack/react-query";
import { base44 } from "@/api/base44Client";

export function useEpg(playlist, streams) {
    const { data: epgData, isLoading, error } = useQuery({
        // The query key is now more specific, depending on the playlist and streams.
        queryKey: ["epg", playlist?.id, streams?.map(s => s.epg_channel_id || s.channel_info?.epg_channel_id)],
        queryFn: async () => {
            // Guard clauses to prevent running the query with incomplete data.
            if (!playlist?.epg_url || !streams || streams.length === 0) {
                return {};
            }
            
            // Extract all unique TVG IDs from the provided streams.
            const tvgIds = streams
                .map(s => s.epg_channel_id || s.channel_info?.epg_channel_id)
                .filter(Boolean); // Remove any null/undefined IDs

            if (tvgIds.length === 0) return {};

            // Invoke the backend function with the correct parameters.
            const { data } = await base44.functions.invoke("fetchEPG", {
                epg_url: playlist.epg_url,
                tvg_ids: [...new Set(tvgIds)], // Pass only unique IDs
            });
            return data;
        },
        // The query is only enabled if a playlist with an EPG URL and a list of streams exist.
        enabled: !!playlist?.epg_url && !!streams && streams.length > 0,
        staleTime: 1000 * 60 * 30, // EPG data is considered fresh for 30 minutes.
        refetchOnWindowFocus: false, // Don't refetch every time the user switches tabs.
    });

    return { epgData, isLoadingEpg: isLoading, epgError: error };
}