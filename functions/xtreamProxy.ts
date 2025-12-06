const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

Deno.serve(async (req) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: CORS_HEADERS });
    }

    try {
        const { host, username, password, params } = await req.json();

        if (!host || !username || !password) {
            return new Response(JSON.stringify({ error: "Host, username, and password are required." }), { status: 400, headers: CORS_HEADERS });
        }

        const url = new URL('/player_api.php', host);
        url.searchParams.set('username', username);
        url.searchParams.set('password', password);

        if (params) {
            for (const key in params) {
                url.searchParams.set(key, params[key]);
            }
        }
        
        const response = await fetch(url.toString(), {
            headers: { 'User-Agent': 'VLC/3.0.0' } // Some providers block requests without a user agent
        });

        if (!response.ok) {
            throw new Error(`Provider API request failed with status: ${response.status}`);
        }
        
        // Some providers return malformed JSON that starts with `[` but doesn't end with `]`.
        // This is a robust way to handle it.
        const text = await response.text();
        let data;
        try {
            // Attempt to parse the text as JSON. This is the ideal case.
            data = JSON.parse(text);
        } catch (jsonError) {
            // If parsing fails, it might be the malformed JSON issue.
            // We check if the text looks like a JSON array but is cut off.
            if (text.trim().startsWith('[') && !text.trim().endsWith(']')) {
                // Try to fix it by adding the closing bracket.
                try {
                    data = JSON.parse(text + ']');
                } catch (fixError) {
                    // If fixing it also fails, we throw the original parsing error.
                    throw new Error(`Failed to parse malformed JSON from provider: ${jsonError.message}`);
                }
            } else {
                // If it's not the specific malformed array issue, throw the original error.
                throw jsonError;
            }
        }

        return new Response(JSON.stringify(data), {
            status: 200,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
        });

    } catch(e) {
        return new Response(JSON.stringify({ error: e.message, stack: e.stack }), {
            status: 500,
            headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
        });
    }
});