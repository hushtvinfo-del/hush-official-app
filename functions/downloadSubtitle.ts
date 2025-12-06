import { createClientFromRequest } from 'npm:@base44/sdk@0.7.1';

const OPENSUBTITLES_API_KEY = Deno.env.get("OPENSUBTITLES_API_KEY");
const USER_AGENT = "HushTV v1.0";

Deno.serve(async (req) => {
    try {
        const base44 = createClientFromRequest(req);
        const user = await base44.auth.me();

        if (!user) {
            return Response.json({ error: 'Unauthorized' }, { status: 401 });
        }

        if (!OPENSUBTITLES_API_KEY) {
            console.error('OPENSUBTITLES_API_KEY not set');
            return Response.json({ 
                error: 'OpenSubtitles API key not configured'
            }, { status: 500 });
        }

        const body = await req.json();
        const { file_id } = body;

        console.log('📥 Subtitle download request for file_id:', file_id);

        if (!file_id) {
            return Response.json({ error: 'file_id is required' }, { status: 400 });
        }

        // Convert file_id to number if it's a string
        const fileIdNum = typeof file_id === 'string' ? parseInt(file_id) : file_id;

        if (isNaN(fileIdNum)) {
            console.error('❌ Invalid file_id:', file_id);
            return Response.json({ 
                error: 'Invalid file_id - must be a number'
            }, { status: 400 });
        }

        console.log('📤 Requesting download link from OpenSubtitles API...');

        // Request download link
        const downloadResponse = await fetch('https://api.opensubtitles.com/api/v1/download', {
            method: 'POST',
            headers: {
                'Api-Key': OPENSUBTITLES_API_KEY,
                'User-Agent': USER_AGENT,
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify({ file_id: fileIdNum })
        });

        console.log('🔍 OpenSubtitles download response status:', downloadResponse.status);

        if (!downloadResponse.ok) {
            const errorText = await downloadResponse.text();
            console.error('❌ OpenSubtitles download API error:', {
                status: downloadResponse.status,
                statusText: downloadResponse.statusText,
                error: errorText
            });

            // Check for common errors
            if (downloadResponse.status === 406) {
                return Response.json({ 
                    error: 'Download limit reached. Please try again later.',
                    details: 'OpenSubtitles daily download quota exceeded'
                }, { status: 429 });
            }

            if (downloadResponse.status === 401) {
                return Response.json({ 
                    error: 'OpenSubtitles authentication failed',
                    details: 'API key may be invalid or expired'
                }, { status: 500 });
            }

            if (downloadResponse.status === 404) {
                return Response.json({ 
                    error: 'Subtitle file not found',
                    details: 'The requested subtitle may have been removed'
                }, { status: 404 });
            }

            return Response.json({ 
                error: `OpenSubtitles API error: ${downloadResponse.statusText}`,
                details: errorText,
                status: downloadResponse.status
            }, { status: 500 });
        }

        const downloadData = await downloadResponse.json();
        console.log('✅ Download data received:', {
            hasLink: !!downloadData.link,
            fileName: downloadData.file_name,
            remaining: downloadData.remaining,
            resetTime: downloadData.reset_time
        });
        
        if (!downloadData.link) {
            console.error('❌ No download link in response:', downloadData);
            return Response.json({ 
                error: 'No download link received from OpenSubtitles',
                details: 'The API did not return a download URL'
            }, { status: 500 });
        }

        console.log('📥 Downloading subtitle content from:', downloadData.link);

        // Download the subtitle file
        const subtitleResponse = await fetch(downloadData.link, {
            headers: {
                'User-Agent': USER_AGENT
            }
        });
        
        if (!subtitleResponse.ok) {
            console.error('❌ Failed to fetch subtitle file:', subtitleResponse.status);
            return Response.json({ 
                error: 'Failed to download subtitle file from provided link',
                details: `Status: ${subtitleResponse.status}`
            }, { status: 500 });
        }

        const subtitleContent = await subtitleResponse.text();
        console.log('✅ Subtitle content downloaded successfully, length:', subtitleContent.length, 'chars');

        // Check if we got valid content
        if (!subtitleContent || subtitleContent.length < 10) {
            console.error('❌ Subtitle content too short or empty');
            return Response.json({ 
                error: 'Downloaded subtitle appears to be empty or invalid'
            }, { status: 500 });
        }

        return Response.json({ 
            subtitle_content: subtitleContent,
            file_name: downloadData.file_name || 'subtitle.srt',
            remaining_downloads: downloadData.remaining,
            reset_time: downloadData.reset_time
        });

    } catch (error) {
        console.error('❌ Subtitle download error:', {
            message: error.message,
            stack: error.stack
        });
        
        return Response.json({ 
            error: 'Subtitle download failed',
            details: error.message || 'Unknown error occurred',
            message: 'Please try again or select a different subtitle'
        }, { status: 500 });
    }
});