import { createClientFromRequest } from 'npm:@base44/sdk@0.8.4';

// Popular sports channels with direct logo URLs
const KNOWN_SPORTS_LOGOS = {
  // TSN Channels
  'tsn': 'https://upload.wikimedia.org/wikipedia/en/thumb/e/e4/TSN_Logo.svg/320px-TSN_Logo.svg.png',
  'tsn1': 'https://upload.wikimedia.org/wikipedia/en/thumb/e/e4/TSN_Logo.svg/320px-TSN_Logo.svg.png',
  'tsn2': 'https://upload.wikimedia.org/wikipedia/en/thumb/e/e4/TSN_Logo.svg/320px-TSN_Logo.svg.png',
  'tsn3': 'https://upload.wikimedia.org/wikipedia/en/thumb/e/e4/TSN_Logo.svg/320px-TSN_Logo.svg.png',
  'tsn4': 'https://upload.wikimedia.org/wikipedia/en/thumb/e/e4/TSN_Logo.svg/320px-TSN_Logo.svg.png',
  'tsn5': 'https://upload.wikimedia.org/wikipedia/en/thumb/e/e4/TSN_Logo.svg/320px-TSN_Logo.svg.png',
  
  // Sportsnet Channels
  'sportsnet': 'https://upload.wikimedia.org/wikipedia/en/thumb/b/bf/Sportsnet_Logo_2012.svg/320px-Sportsnet_Logo_2012.svg.png',
  'sportsnet one': 'https://upload.wikimedia.org/wikipedia/en/thumb/b/bf/Sportsnet_Logo_2012.svg/320px-Sportsnet_Logo_2012.svg.png',
  'sportsnet 360': 'https://upload.wikimedia.org/wikipedia/commons/thumb/2/29/Sportsnet_360.svg/320px-Sportsnet_360.svg.png',
  'sportsnet world': 'https://upload.wikimedia.org/wikipedia/en/thumb/b/bf/Sportsnet_Logo_2012.svg/320px-Sportsnet_Logo_2012.svg.png',
  'sportsnet ontario': 'https://upload.wikimedia.org/wikipedia/en/thumb/b/bf/Sportsnet_Logo_2012.svg/320px-Sportsnet_Logo_2012.svg.png',
  'sportsnet east': 'https://upload.wikimedia.org/wikipedia/en/thumb/b/bf/Sportsnet_Logo_2012.svg/320px-Sportsnet_Logo_2012.svg.png',
  'sportsnet west': 'https://upload.wikimedia.org/wikipedia/en/thumb/b/bf/Sportsnet_Logo_2012.svg/320px-Sportsnet_Logo_2012.svg.png',
  'sportsnet pacific': 'https://upload.wikimedia.org/wikipedia/en/thumb/b/bf/Sportsnet_Logo_2012.svg/320px-Sportsnet_Logo_2012.svg.png',
  
  // ESPN Channels
  'espn': 'https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/ESPN_wordmark.svg/320px-ESPN_wordmark.svg.png',
  'espn2': 'https://upload.wikimedia.org/wikipedia/commons/thumb/9/93/ESPN2_logo.svg/320px-ESPN2_logo.svg.png',
  'espn news': 'https://upload.wikimedia.org/wikipedia/commons/thumb/9/92/ESPNews.svg/320px-ESPNews.svg.png',
  'espn u': 'https://upload.wikimedia.org/wikipedia/commons/thumb/7/78/ESPNU_logo.svg/320px-ESPNU_logo.svg.png',
  
  // Fox Sports
  'fox sports': 'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/Fox_Sports_logo.svg/320px-Fox_Sports_logo.svg.png',
  'fox sports 1': 'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/Fox_Sports_logo.svg/320px-Fox_Sports_logo.svg.png',
  'fox sports 2': 'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/Fox_Sports_logo.svg/320px-Fox_Sports_logo.svg.png',
  'fs1': 'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/Fox_Sports_logo.svg/320px-Fox_Sports_logo.svg.png',
  'fs2': 'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/Fox_Sports_logo.svg/320px-Fox_Sports_logo.svg.png',
  
  // NBC Sports
  'nbc sports': 'https://upload.wikimedia.org/wikipedia/commons/thumb/9/9e/NBC_Sports_2012.svg/320px-NBC_Sports_2012.svg.png',
  'nbcsn': 'https://upload.wikimedia.org/wikipedia/commons/thumb/9/9e/NBC_Sports_2012.svg/320px-NBC_Sports_2012.svg.png',
  
  // CBS Sports
  'cbs sports': 'https://upload.wikimedia.org/wikipedia/commons/thumb/f/f5/CBS_Sports_logo.svg/320px-CBS_Sports_logo.svg.png',
  
  // BeIN Sports
  'bein sports': 'https://upload.wikimedia.org/wikipedia/commons/thumb/d/d1/BeIN_Sports_logo.svg/320px-BeIN_Sports_logo.svg.png',
  'bein': 'https://upload.wikimedia.org/wikipedia/commons/thumb/d/d1/BeIN_Sports_logo.svg/320px-BeIN_Sports_logo.svg.png',
  
  // DAZN
  'dazn': 'https://upload.wikimedia.org/wikipedia/commons/thumb/8/83/DAZN_Logo.svg/320px-DAZN_Logo.svg.png',
  
  // NFL Network
  'nfl network': 'https://upload.wikimedia.org/wikipedia/commons/thumb/8/8d/NFL_Network_logo.svg/320px-NFL_Network_logo.svg.png',
  'nfl': 'https://upload.wikimedia.org/wikipedia/commons/thumb/8/8d/NFL_Network_logo.svg/320px-NFL_Network_logo.svg.png',
  
  // NBA TV
  'nba tv': 'https://upload.wikimedia.org/wikipedia/en/thumb/d/d2/NBA_TV.svg/320px-NBA_TV.svg.png',
  'nba': 'https://upload.wikimedia.org/wikipedia/en/thumb/d/d2/NBA_TV.svg/320px-NBA_TV.svg.png',
  
  // MLB Network
  'mlb network': 'https://upload.wikimedia.org/wikipedia/commons/thumb/5/5a/MLB_Network_logo.svg/320px-MLB_Network_logo.svg.png',
  'mlb': 'https://upload.wikimedia.org/wikipedia/commons/thumb/5/5a/MLB_Network_logo.svg/320px-MLB_Network_logo.svg.png',
  
  // NHL Network
  'nhl network': 'https://upload.wikimedia.org/wikipedia/en/thumb/1/1c/NHL_Network.svg/320px-NHL_Network.svg.png',
  'nhl': 'https://upload.wikimedia.org/wikipedia/en/thumb/1/1c/NHL_Network.svg/320px-NHL_Network.svg.png',
  
  // Tennis Channel
  'tennis channel': 'https://upload.wikimedia.org/wikipedia/commons/thumb/8/8c/Tennis_Channel_logo.svg/320px-Tennis_Channel_logo.svg.png',
  
  // Golf Channel
  'golf channel': 'https://upload.wikimedia.org/wikipedia/commons/thumb/0/0a/Golf_Channel_logo.svg/320px-Golf_Channel_logo.svg.png',
  
  // BT Sport
  'bt sport': 'https://upload.wikimedia.org/wikipedia/en/thumb/5/54/BT_Sport_logo_2019.svg/320px-BT_Sport_logo_2019.svg.png',
  
  // Sky Sports
  'sky sports': 'https://upload.wikimedia.org/wikipedia/commons/thumb/2/23/Sky_Sports_logo_2020.svg/320px-Sky_Sports_logo_2020.svg.png',
  
  // Eurosport
  'eurosport': 'https://upload.wikimedia.org/wikipedia/commons/thumb/d/d0/Eurosport_Logo_2015.svg/320px-Eurosport_Logo_2015.svg.png',
  
  // Premier Sports
  'premier sports': 'https://upload.wikimedia.org/wikipedia/en/thumb/5/5b/Premier_Sports_logo.svg/320px-Premier_Sports_logo.svg.png',
  
  // Fight Network
  'fight network': 'https://upload.wikimedia.org/wikipedia/commons/thumb/0/0e/Fight_Network_logo.svg/320px-Fight_Network_logo.svg.png',
};

Deno.serve(async (req) => {
  try {
    const base44 = createClientFromRequest(req);
    
    const { channel_name } = await req.json();
    
    if (!channel_name) {
      return Response.json({ 
        error: 'channel_name is required' 
      }, { status: 400 });
    }

    // Normalize the channel name for lookup
    const normalizedName = channel_name
      .toLowerCase()
      .trim()
      .replace(/\s+/g, ' ') // Normalize spaces
      .replace(/hd$/i, '') // Remove HD suffix
      .replace(/fhd$/i, '') // Remove FHD suffix
      .replace(/4k$/i, '') // Remove 4K suffix
      .replace(/\d+$/, '') // Remove trailing numbers (e.g., TSN1 -> TSN)
      .trim();

    // Check if we have a known logo
    if (KNOWN_SPORTS_LOGOS[normalizedName]) {
      return Response.json({ 
        logo_url: KNOWN_SPORTS_LOGOS[normalizedName],
        source: 'cached'
      });
    }

    // Also try with numbers (for TSN1, TSN2, etc.)
    const withNumbers = channel_name
      .toLowerCase()
      .trim()
      .replace(/\s+/g, ' ')
      .replace(/hd$/i, '')
      .replace(/fhd$/i, '')
      .replace(/4k$/i, '')
      .trim();
    
    if (KNOWN_SPORTS_LOGOS[withNumbers]) {
      return Response.json({ 
        logo_url: KNOWN_SPORTS_LOGOS[withNumbers],
        source: 'cached'
      });
    }

    // If not in our cache, try to find it using AI with internet search
    try {
      const searchResult = await base44.integrations.Core.InvokeLLM({
        prompt: `Find the official logo URL for the sports TV channel "${channel_name}". 
        Look for high-quality PNG or SVG logos from Wikipedia, official websites, or reliable sources.
        Return ONLY a direct image URL (starting with https://) that can be used in an <img> tag.
        Prefer Wikipedia logos if available.
        If you cannot find a logo, return the text "NOT_FOUND".`,
        add_context_from_internet: true,
      });

      let logoUrl = searchResult.trim();
      
      // Validate that we got a URL
      if (logoUrl && logoUrl !== 'NOT_FOUND' && logoUrl.startsWith('http')) {
        return Response.json({ 
          logo_url: logoUrl,
          source: 'ai_search'
        });
      }

      // No logo found
      return Response.json({ 
        logo_url: null,
        source: 'not_found'
      });

    } catch (searchError) {
      console.error('AI search failed:', searchError);
      return Response.json({ 
        logo_url: null,
        source: 'search_failed',
        error: searchError.message
      });
    }

  } catch (error) {
    console.error('Error:', error);
    return Response.json({ 
      error: error.message 
    }, { status: 500 });
  }
});