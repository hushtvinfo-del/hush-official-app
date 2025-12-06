import { XMLParser } from 'npm:fast-xml-parser@4.3.6';

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

// Helper to convert EPG time string (YYYYMMDDHHMMSS +/-ZZZZ) to a Date object
function parseEpgTime(timeStr) {
  try {
    const year = parseInt(timeStr.substring(0, 4), 10);
    const month = parseInt(timeStr.substring(4, 6), 10) - 1; // JS months are 0-11
    const day = parseInt(timeStr.substring(6, 8), 10);
    const hour = parseInt(timeStr.substring(8, 10), 10);
    const minute = parseInt(timeStr.substring(10, 12), 10);
    const second = parseInt(timeStr.substring(12, 14), 10);
    
    // Create a date in UTC to avoid local timezone issues during parsing
    return new Date(Date.UTC(year, month, day, hour, minute, second));
  } catch(e) {
    return new Date('invalid');
  }
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: CORS_HEADERS });
  }

  try {
    const { epg_url, tvg_ids } = await req.json();

    if (!epg_url || !tvg_ids || !Array.isArray(tvg_ids)) {
      return new Response(JSON.stringify({ error: "epg_url and an array of tvg_ids are required" }), {
        status: 400,
        headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
      });
    }
    
    // Fetch with a common User-Agent header, some providers might require it.
    const response = await fetch(epg_url, {
      headers: { 'User-Agent': 'VLC/3.0.0' }
    });

    if (!response.ok) {
      throw new Error(`Failed to fetch EPG data. Status: ${response.status}`);
    }
    const xmlData = await response.text();

    const parser = new XMLParser({
        ignoreAttributes: false,
        attributeNamePrefix: "@_",
        textNodeName: "#text",
        parseAttributeValue: true,
        isArray: (name, jpath) => jpath === 'tv.programme' || jpath === 'tv.channel'
    });
    const epgJson = parser.parse(xmlData);

    const programmes = epgJson?.tv?.programme || [];
    const requestedIds = new Set(tvg_ids);
    const now = new Date();
    
    const channelPrograms = {};

    // Group programs by channel
    for (const prog of programmes) {
        const channelId = prog['@_channel'];
        if (requestedIds.has(channelId)) {
            if (!channelPrograms[channelId]) {
                channelPrograms[channelId] = [];
            }
            channelPrograms[channelId].push(prog);
        }
    }

    const finalEpgData = {};

    // Find current and next program for each channel
    for (const channelId in channelPrograms) {
        // Sort programs by start time just in case they are not in order
        const sortedPrograms = channelPrograms[channelId].sort((a, b) => {
            const timeA = parseEpgTime(a['@_start']);
            const timeB = parseEpgTime(b['@_start']);
            return timeA - timeB;
        });

        let currentProg = null;
        let nextProg = null;

        for (let i = 0; i < sortedPrograms.length; i++) {
            const prog = sortedPrograms[i];
            const startTime = parseEpgTime(prog['@_start']);
            const stopTime = parseEpgTime(prog['@_stop']);

            if (now >= startTime && now < stopTime) {
                currentProg = prog;
                if (i + 1 < sortedPrograms.length) {
                    nextProg = sortedPrograms[i+1];
                }
                break;
            }
        }

        if (currentProg) {
             const title = (currentProg.title && (currentProg.title['#text'] || currentProg.title)) || 'No Title';
             const desc = (currentProg.desc && (currentProg.desc['#text'] || currentProg.desc)) || 'No description available.';
            
             finalEpgData[channelId] = {
                current: {
                    title: title.trim(),
                    desc: desc.trim(),
                    start: parseEpgTime(currentProg['@_start']).toISOString(),
                    stop: parseEpgTime(currentProg['@_stop']).toISOString(),
                    episode: currentProg['episode-num'] ? (currentProg['episode-num']['#text'] || currentProg['episode-num']) : null
                }
             };

             // Add next program if available
             if (nextProg) {
                 const nextTitle = (nextProg.title && (nextProg.title['#text'] || nextProg.title)) || 'No Title';
                 const nextDesc = (nextProg.desc && (nextProg.desc['#text'] || nextProg.desc)) || 'No description available.';
                 
                 finalEpgData[channelId].next = {
                     title: nextTitle.trim(),
                     desc: nextDesc.trim(),
                     start: parseEpgTime(nextProg['@_start']).toISOString(),
                     stop: parseEpgTime(nextProg['@_stop']).toISOString(),
                     episode: nextProg['episode-num'] ? (nextProg['episode-num']['#text'] || nextProg['episode-num']) : null
                 };
             }
        }
    }

    return new Response(JSON.stringify(finalEpgData), {
      status: 200,
      headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });

  } catch (e) {
    return new Response(JSON.stringify({ error: e.message, stack: e.stack }), {
      status: 500,
      headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  }
});