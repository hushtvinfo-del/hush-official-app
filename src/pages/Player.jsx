import React, { useRef, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { ArrowLeft, AlertCircle, Cast, Download, Settings } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { base44 } from "@/api/base44Client";
import _ from 'lodash';

const isIOS = () => {
  return /iPad|iPhone|iPod/.test(navigator.userAgent) || 
         (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
};

const getPlaylistFromLocal = (playlistId) => {
  try {
    const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
    return localPlaylists.find(p => p.id === playlistId);
  } catch(e) {
    console.error("Failed to parse playlists from localStorage:", e);
    return null;
  }
};

const getPreferredPlayer = () => {
  try {
    return localStorage.getItem('preferredExternalPlayer');
  } catch (e) {
    return null;
  }
};

const setPreferredPlayer = (playerName) => {
  try {
    localStorage.setItem('preferredExternalPlayer', playerName);
    console.log(`✅ Saved ${playerName} as preferred player`);
  } catch (e) {
    console.error('Failed to save preferred player:', e);
  }
};

export default function Player() {
  const navigate = useNavigate();
  const videoRef = useRef(null);
  const playerRef = useRef(null);
  const subtitleLoadedRef = useRef(false);
  const nativeEventListenersRef = useRef([]);
  const queryClient = useQueryClient();
  const urlParams = new URLSearchParams(window.location.search);

  const channelUrl = decodeURIComponent(urlParams.get('channelUrl') || '');
  const channelName = decodeURIComponent(urlParams.get('channelName') || '');
  const containerExtension = urlParams.get('containerExtension');
  const playlistId = urlParams.get('playlistId');
  const startTime = urlParams.get('t');
  const contentType = urlParams.get('contentType');
  const coverImage = urlParams.get('coverImage') ? decodeURIComponent(urlParams.get('coverImage')) : '';
  const seriesId = urlParams.get('seriesId');

  const isVOD = containerExtension !== 'm3u8' && containerExtension !== null;
  const deviceIsIOS = isIOS();
  const isHLS = containerExtension === 'm3u8';

  const [castAvailable, setCastAvailable] = useState(false);
  const [casting, setCasting] = useState(false);
  const [showPlayerChoice, setShowPlayerChoice] = useState(false);
  const [vlcNotInstalled, setVlcNotInstalled] = useState(false);

  const playlist = React.useMemo(() => getPlaylistFromLocal(playlistId), [playlistId]);
  const userIdentifier = playlist?.username;

  const recommendedPlayers = [
    {
      name: 'VLC',
      urlScheme: 'vlc-x-callback://x-callback-url/stream?url=',
      appStoreUrl: 'https://apps.apple.com/app/vlc-media-player/id650377962',
      description: 'Free & Open Source'
    },
  ];

  const isFormatSupportedOnIOS = (extension) => {
    const supportedFormats = ['m3u8', 'mp4', 'mov'];
    return supportedFormats.includes(extension);
  };

  const shouldRedirectToExternalPlayer = deviceIsIOS && containerExtension && !isFormatSupportedOnIOS(containerExtension);

  const normalizeUrl = (url) => {
    if (!url) return '';
    if (!/^https?:\/\//i.test(url)) {
      return 'http://' + url;
    }
    return url;
  };

  const buildPlayerUrl = (player, streamUrl) => {
    const normalizedUrl = normalizeUrl(streamUrl);
    
    if (!normalizedUrl || normalizedUrl === 'http://') {
      return null;
    }

    return player.urlScheme + encodeURIComponent(normalizedUrl);
  };

  const handlePlayerSelected = (playerName) => {
    setPreferredPlayer(playerName);
  };

  useEffect(() => {
    if (shouldRedirectToExternalPlayer) {
      // Always try to auto-open VLC first for iOS
      const vlcPlayer = recommendedPlayers[0]; // VLC is first in array
      const playerUrl = buildPlayerUrl(vlcPlayer, channelUrl);
      
      if (playerUrl) {
        console.log(`✅ Auto-opening VLC player`);
        
        // Try to open VLC
        window.location.href = playerUrl;
        
        // After a short delay, check if we're still on the page
        // If we are, VLC probably isn't installed
        setTimeout(() => {
          // If we're still here after 2 seconds, VLC likely isn't installed
          setVlcNotInstalled(true);
          setShowPlayerChoice(true);
        }, 2000);
      } else {
        setShowPlayerChoice(true);
      }
    }
  }, [shouldRedirectToExternalPlayer, channelUrl]);

  const getStreamIdFromUrl = (url) => {
    try {
      const parts = url.split('/');
      const filename = parts[parts.length - 1];
      const streamId = filename.split('.')[0];
      return streamId;
    } catch (e) {
      console.error("❌ Error getting stream ID from URL:", e);
      return null;
    }
  };

  const upsertProgressMutation = useMutation({
    mutationFn: async (progressData) => {
        if (!userIdentifier) {
            return;
        }

        const filters = {
            user_email: userIdentifier,
            content_id: progressData.content_id,
            playlist_id: playlistId
        };
        const existing = await base44.entities.WatchProgress.filter(filters, '-created_date', 1);

        if (existing.length > 0) {
            return base44.entities.WatchProgress.update(existing[0].id, {
                progress: progressData.progress,
                duration: progressData.duration
            });
        } else {
            return base44.entities.WatchProgress.create(progressData);
        }
    },
    onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['watchProgress'] });
    }
  });

  const convertSrtToVtt = (srtContent) => {
    let vttContent = 'WEBVTT\n\n';
    vttContent += srtContent.replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, '$1.$2');
    return vttContent;
  };

  const loadSubtitlesForContent = (player) => {
    if (!isVOD || !channelName || !player || subtitleLoadedRef.current) {
      return;
    }

    if (player.isDisposed && player.isDisposed()) {
      return;
    }

    subtitleLoadedRef.current = true;

    base44.functions.invoke('getSubtitles', {
      query: channelName,
      type: contentType || 'movie'
    }).then(response => {
      if (!response.data?.subtitles || response.data.subtitles.length === 0) {
        return;
      }

      const firstSubtitle = response.data.subtitles[0];

      base44.functions.invoke('downloadSubtitle', {
        file_id: firstSubtitle.download_url
      }).then(downloadResponse => {
        if (!downloadResponse.data?.subtitle_content) {
          return;
        }

        if (!playerRef.current || (playerRef.current.isDisposed && playerRef.current.isDisposed())) {
          return;
        }

        const vttContent = convertSrtToVtt(downloadResponse.data.subtitle_content);
        const blob = new Blob([vttContent], { type: 'text/vtt;charset=utf-8' });
        const url = URL.createObjectURL(blob);

        const player = playerRef.current;
        if (!player || (player.isDisposed && player.isDisposed())) {
          URL.revokeObjectURL(url);
          return;
        }

        const tracks = player.remoteTextTracks();
        for (let i = tracks.length - 1; i >= 0; i--) {
          player.removeRemoteTextTrack(tracks[i]);
        }

        player.addRemoteTextTrack({
          kind: 'subtitles',
          label: 'English',
          srclang: 'en',
          src: url,
          mode: 'disabled'
        }, false);

        const subtitlesButton = player.controlBar?.getChild('SubtitlesButton');
        const captionsButton = player.controlBar?.getChild('CaptionsButton');

        if (subtitlesButton) {
          subtitlesButton.show();
        } else if (captionsButton) {
          captionsButton.show();
        }

      }).catch(error => {
        console.error('Failed to download subtitle:', error.message);
      });

    }).catch(error => {
      console.error('Failed to fetch subtitles:', error.message);
    });
  };

  const handleCast = () => {
    if (!window.chrome || !window.chrome.cast) {
      alert('Cast API not loaded yet. Please wait a moment and try again.');
      return;
    }

    const cast = window.chrome.cast;

    cast.requestSession(
      (session) => {
        const currentTime = playerRef.current ? playerRef.current.currentTime() : (startTime ? parseFloat(startTime) : 0);

        let mediaContentType = 'video/mp4';

        if (containerExtension === 'm3u8') {
          mediaContentType = 'application/x-mpegURL';
        } else if (containerExtension === 'mkv') {
          mediaContentType = 'video/x-matroska';
        } else if (containerExtension === 'avi') {
          mediaContentType = 'video/x-msvideo';
        } else if (containerExtension === 'webm') {
          mediaContentType = 'video/webm';
        }

        const mediaInfo = new cast.media.MediaInfo(channelUrl, mediaContentType);

        mediaInfo.metadata = new cast.media.GenericMediaMetadata();
        mediaInfo.metadata.title = channelName;
        mediaInfo.metadata.subtitle = 'HushTV Player';

        if (coverImage) {
          try {
            mediaInfo.metadata.images = [new cast.media.Image(coverImage)];
          } catch (e) {
            console.warn('Could not add cover image:', e);
          }
        }

        mediaInfo.streamType = isVOD ? cast.media.StreamType.BUFFERED : cast.media.StreamType.LIVE;

        const request = new cast.media.LoadRequest(mediaInfo);
        request.currentTime = currentTime;
        request.autoplay = true;

        session.loadMedia(
          request,
          (media) => {
            setCasting(true);
            if (playerRef.current && playerRef.current.pause) {
              playerRef.current.pause();
            }

            media.addUpdateListener((isAlive) => {
              if (!isAlive) {
                console.log('Media session ended');
              }
            });
          },
          (error) => {
            console.error('❌ Chromecast load error:', error);
            setCasting(false);

            let errorMessage = 'Failed to cast media. ';

            if (error.code === 'LOAD_FAILED') {
              errorMessage += 'The stream could not be loaded. This might be due to:\n' +
                             '• Network/CORS restrictions\n' +
                             '• Unsupported format (' + containerExtension + ')\n' +
                             '• Stream authentication issues';
            } else if (error.code === 'TIMEOUT') {
              errorMessage += 'Connection timed out. Check your network.';
            } else {
              errorMessage += error.description || error.code || 'Unknown error';
            }

            alert(errorMessage);
          }
        );
      },
      (error) => {
        console.error('❌ Error requesting cast session:', error);
        alert('Failed to connect to Chromecast. Make sure your device is on the same network.');
      }
    );
  };

  useEffect(() => {
    const initializeCast = () => {
      if (window.chrome && window.chrome.cast && window.chrome.cast.isAvailable) {
        const cast = window.chrome.cast;

        const sessionRequest = new cast.SessionRequest('CC1AD845');

        const apiConfig = new cast.ApiConfig(
          sessionRequest,
          (session) => {
            setCasting(true);

            session.addUpdateListener((isAlive) => {
              if (!isAlive) {
                setCasting(false);
                if (playerRef.current && playerRef.current.play) {
                  playerRef.current.play().catch(e => console.log('Could not resume playback:', e));
                }
              }
            });
          },
          (status) => {
            if (status === cast.ReceiverAvailability.AVAILABLE) {
              setCastAvailable(true);
            } else if (status === cast.ReceiverAvailability.UNAVAILABLE) {
              setCastAvailable(false);
            }
          },
          cast.AutoJoinPolicy.ORIGIN_SCOPED
        );

        cast.initialize(apiConfig,
          () => {},
          (error) => {
            console.error('❌ Cast initialization error:', error);
          }
        );
      }
    };

    if (!window.chrome || !window.chrome.cast || !window.chrome.cast.isAvailable) {
      const script = document.createElement('script');
      script.src = 'https://www.gstatic.com/cv/js/sender/v1/cast_sender.js?loadCastFramework=1';
      script.async = true;
      document.body.appendChild(script);

      window['__onGCastApiAvailable'] = (isAvailable) => {
        if (isAvailable) {
          setTimeout(initializeCast, 1000);
        }
      };
    } else {
      initializeCast();
    }

    return () => {
      if (window.chrome && window.chrome.cast && window.chrome.cast.isAvailable) {
        try {
          const session = window.chrome.cast.framework?.CastContext?.getInstance()?.getCurrentSession();
          if (session) {
            session.endSession(true);
          }
        } catch (e) {
            console.warn("Error ending cast session on component unmount:", e);
        }
      }
    };
  }, []);

  useEffect(() => {
    if (!channelUrl) {
      console.error('No channel URL provided');
      return;
    }

    if (shouldRedirectToExternalPlayer) {
      return;
    }

    subtitleLoadedRef.current = false;

    const cssId = 'videojs-css';
    if (!document.getElementById(cssId)) {
        const link = document.createElement('link');
        link.id = cssId;
        link.rel = 'stylesheet';
        link.href = 'https://vjs.zencdn.net/8.10.0/video-js.css';
        document.head.appendChild(link);
    }

    const initializePlayer = () => {
        if (!videoRef.current || playerRef.current) return;

        if (deviceIsIOS && isHLS) {
            const videoElement = videoRef.current;
            
            // Log the stream URL for debugging
            console.log('🎬 Loading stream:', channelUrl);
            console.log('📝 Container extension:', containerExtension);
            
            videoElement.src = channelUrl;
            videoElement.controls = true;
            videoElement.playsInline = true;
            
            // Add error event listener before trying to play
            const handleError = (e) => {
                console.error('❌ Video error:', {
                    error: videoElement.error,
                    code: videoElement.error?.code,
                    message: videoElement.error?.message,
                    networkState: videoElement.networkState,
                    readyState: videoElement.readyState
                });
            };
            videoElement.addEventListener('error', handleError);
            nativeEventListenersRef.current.push({ event: 'error', handler: handleError });
            
            // Add load event listener to confirm stream is loading
            const handleLoadStart = () => {
                console.log('📡 Stream loading started...');
            };
            videoElement.addEventListener('loadstart', handleLoadStart);
            nativeEventListenersRef.current.push({ event: 'loadstart', handler: handleLoadStart });
            
            const handleLoadedMetadata = () => {
                console.log('✅ Stream metadata loaded successfully');
            };
            videoElement.addEventListener('loadedmetadata', handleLoadedMetadata);
            nativeEventListenersRef.current.push({ event: 'loadedmetadata', handler: handleLoadedMetadata });
            
            const playPromise = videoElement.play();
            if (playPromise !== undefined) {
                playPromise.then(() => {
                    console.log('▶️ Playback started successfully');
                }).catch(error => {
                    console.log('⚠️ Autoplay prevented, user interaction required:', error);
                });
            }

            playerRef.current = {
                isDisposed: () => false,
                dispose: () => {
                    if (videoElement) {
                        videoElement.pause();
                        videoElement.src = '';
                    }
                },
                currentTime: () => videoElement.currentTime,
                duration: () => videoElement.duration,
                pause: () => videoElement.pause(),
                play: () => videoElement.play(),
            };

            if (isVOD && userIdentifier) {
              const streamId = getStreamIdFromUrl(channelUrl);
              if (streamId) {
                const handleTimeUpdateNative = _.throttle(() => {
                  const progress = videoElement.currentTime;
                  const duration = videoElement.duration;
                  if (!isNaN(duration) && duration > 0 && progress > 5) {
                    upsertProgressMutation.mutate({
                       progress,
                       duration,
                       user_email: userIdentifier,
                       content_id: streamId,
                       playlist_id: playlistId,
                       content_type: contentType || 'movie',
                       content_info: {
                           name: channelName,
                           cover_image: coverImage || '',
                           series_id: seriesId || '',
                           container_extension: containerExtension,
                       }
                    });
                  }
                }, 15000, { trailing: true });
                videoElement.addEventListener('timeupdate', handleTimeUpdateNative);
                nativeEventListenersRef.current.push({ event: 'timeupdate', handler: handleTimeUpdateNative });

                const handleEndedNative = () => {
                  const duration = videoElement.duration;
                  if (!isNaN(duration) && duration > 0) {
                     upsertProgressMutation.mutate({
                       progress: duration,
                       duration: duration,
                       user_email: userIdentifier,
                       content_id: streamId,
                       playlist_id: playlistId,
                       content_type: contentType || 'movie',
                       content_info: {
                           name: channelName,
                           cover_image: coverImage || '',
                           series_id: seriesId || '',
                           container_extension: containerExtension
                       }
                    });
                  }
                };
                videoElement.addEventListener('ended', handleEndedNative);
                nativeEventListenersRef.current.push({ event: 'ended', handler: handleEndedNative });
              }
            }

            return;
        }

        const options = {
            autoplay: true,
            controls: true,
            responsive: true,
            fluid: true,
            preload: 'auto',
            playsinline: true,
            muted: false,
            controlBar: {
                volumePanel: {
                    inline: false,
                    vertical: true
                },
                subtitlesButton: true,
                captionsButton: true,
                fullscreenToggle: true,
                pictureInPictureToggle: true
            },
            html5: {
                vhs: {
                    overrideNative: !deviceIsIOS
                },
                nativeVideoTracks: true,
                nativeAudioTracks: true,
                nativeTextTracks: true
            },
            textTrackSettings: false
        };

        const player = window.videojs(videoRef.current, options);
        playerRef.current = player;

        // Determine source type based on URL and container extension
        let sourceType = 'video/mp4';
        if (isHLS) {
            sourceType = 'application/x-mpegURL';
        } else if (containerExtension === 'mkv') {
            sourceType = 'video/x-matroska';
        } else if (containerExtension === 'avi') {
            sourceType = 'video/x-msvideo';
        } else if (containerExtension === 'webm') {
            sourceType = 'video/webm';
        } else if (deviceIsIOS) {
            sourceType = 'video/mp4';
        }

        console.log('🎬 Loading video with type:', sourceType);
        console.log('📺 Stream URL:', channelUrl.substring(0, 100) + '...');

        player.src({
            src: channelUrl,
            type: sourceType
        });

        player.on('ready', () => {
            setTimeout(() => {
              if (playerRef.current && !playerRef.current.isDisposed()) {
                loadSubtitlesForContent(playerRef.current);
              }
            }, 2000);

            if (isVOD && startTime) {
                const parsedStartTime = parseFloat(startTime);
                if (!isNaN(parsedStartTime) && parsedStartTime > 0) {
                    player.currentTime(parsedStartTime);
                }
            }
        });

        player.on('error', (e) => {
            const error = player.error();
            console.error('❌ VideoJS Player error:', {
                code: error?.code,
                message: error?.message,
                type: error?.type,
                networkState: player.networkState,
                readyState: player.readyState
            });
        });

        player.on('loadstart', () => {
            console.log('📡 VideoJS: Stream loading started...');
        });

        player.on('loadedmetadata', () => {
            console.log('✅ VideoJS: Stream metadata loaded successfully');
        });

        if (isVOD && userIdentifier) {
            const streamId = getStreamIdFromUrl(channelUrl);

            if (streamId) {
                const handleTimeUpdate = _.throttle(() => {
                    const progress = player.currentTime();
                    const duration = player.duration();

                    if (!isNaN(duration) && duration > 0 && progress > 5) {
                        upsertProgressMutation.mutate({
                           progress,
                           duration,
                           user_email: userIdentifier,
                           content_id: streamId,
                           playlist_id: playlistId,
                           content_type: contentType || 'movie',
                           content_info: {
                               name: channelName,
                               cover_image: coverImage || '',
                               series_id: seriesId || '',
                               container_extension: containerExtension,
                           }
                        });
                    }
                }, 15000, { trailing: true });

                player.on('timeupdate', handleTimeUpdate);

                player.on('ended', () => {
                    const duration = player.duration();
                    if (!isNaN(duration) && duration > 0) {
                         upsertProgressMutation.mutate({
                           progress: duration,
                           duration: duration,
                           user_email: userIdentifier,
                           content_id: streamId,
                           playlist_id: playlistId,
                           content_type: contentType || 'movie',
                           content_info: {
                               name: channelName,
                               cover_image: coverImage || '',
                               series_id: seriesId || '',
                               container_extension: containerExtension
                           }
                        });
                    }
                });
            }
        }
    };

    if (deviceIsIOS && isHLS) {
        initializePlayer();
    } else if (window.videojs) {
        initializePlayer();
    } else {
        const scriptId = 'videojs-script';
        if (!document.getElementById(scriptId)) {
            const script = document.createElement('script');
            script.id = scriptId;
            script.src = 'https://vjs.zencdn.net/8.10.0/video.min.js';
            script.async = true;
            document.body.appendChild(script);
            script.onload = initializePlayer;
        } else {
            const existingScript = document.getElementById(scriptId);
            if (existingScript && (existingScript.readyState === 'complete' || existingScript.readyState === 'loaded')) {
                initializePlayer();
            } else if (existingScript) {
                existingScript.addEventListener('load', initializePlayer);
            }
        }
    }

    return () => {
        const player = playerRef.current;
        if (player && player.isDisposed && !player.isDisposed()) {
            player.dispose();
            playerRef.current = null;
        } else if (videoRef.current && deviceIsIOS && isHLS) {
            videoRef.current.pause();
            videoRef.current.src = '';
            nativeEventListenersRef.current.forEach(({ event, handler }) => {
              videoRef.current?.removeEventListener(event, handler);
            });
            nativeEventListenersRef.current = [];
            playerRef.current = null;
        }
    };
  }, [channelUrl, containerExtension, userIdentifier, playlistId, startTime, isVOD, channelName, contentType, coverImage, seriesId, deviceIsIOS, isHLS, shouldRedirectToExternalPlayer]);

  if (showPlayerChoice) {
    const normalizedUrl = normalizeUrl(channelUrl);
    const vlcPlayer = recommendedPlayers[0];
    const playerUrl = buildPlayerUrl(vlcPlayer, normalizedUrl);
    
    return (
      <div className="min-h-screen bg-gradient-to-br from-black via-orange-950 to-black flex items-center justify-center p-6">
        <div className="max-w-xl w-full">
          <Alert className="mb-6 border-orange-500/30 bg-gray-800/50">
            <AlertCircle className="h-5 w-5 text-orange-400" />
            <AlertTitle className="text-white text-lg">
              {vlcNotInstalled ? 'VLC Not Installed' : 'External Player Required'}
            </AlertTitle>
            <AlertDescription className="text-gray-300 mt-2">
              {vlcNotInstalled ? (
                <>
                  VLC is not installed on your device. This video format ({containerExtension?.toUpperCase()}) requires VLC to play.
                  <br /><br />
                  Please install VLC from the App Store to watch this content.
                </>
              ) : (
                <>
                  Your iOS device does not natively support this video format ({containerExtension?.toUpperCase()}).
                  <br /><br />
                  Opening in VLC...
                </>
              )}
            </AlertDescription>
          </Alert>

          {vlcNotInstalled ? (
            <>
              {/* VLC Not Installed - Show Install Button */}
              <a
                href={vlcPlayer.appStoreUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="block mb-4"
              >
                <Button
                  size="lg"
                  className="w-full bg-gradient-to-r from-orange-600 to-orange-800 hover:from-orange-700 hover:to-orange-900 text-white shadow-lg h-auto py-6"
                >
                  <div className="flex flex-col items-center w-full">
                    <div className="flex items-center mb-2">
                      <Download className="w-6 h-6 mr-2" />
                      <span className="text-xl font-bold">Download VLC Player</span>
                    </div>
                    <span className="text-sm text-orange-100">Free from the App Store</span>
                  </div>
                </Button>
              </a>

              <div className="bg-gray-800/50 rounded-lg p-5 mb-6 border border-orange-500/20">
                <p className="text-white font-semibold mb-3 text-center">After installing VLC:</p>
                <ol className="list-decimal list-inside space-y-2 text-gray-300 text-sm">
                  <li>Open VLC from your home screen</li>
                  <li>Return to HushTV Player</li>
                  <li>Try playing this content again</li>
                </ol>
              </div>

              {playerUrl && (
                <a href={playerUrl} className="block mb-4">
                  <Button
                    size="lg"
                    variant="outline"
                    className="w-full border-orange-500/30 text-orange-300 hover:bg-orange-500/20"
                  >
                    I already have VLC - Try Opening
                  </Button>
                </a>
              )}
            </>
          ) : (
            <>
              {/* VLC Should Be Opening - Show Retry Button */}
              <div className="bg-gray-800/50 rounded-lg p-5 mb-6 border border-orange-500/20">
                <div className="flex items-center justify-center mb-3">
                  <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-orange-500"></div>
                </div>
                <p className="text-gray-300 text-center text-sm">
                  If VLC doesn't open automatically, tap the button below:
                </p>
              </div>

              {playerUrl && (
                <a href={playerUrl} className="block mb-4">
                  <Button
                    size="lg"
                    className="w-full bg-gradient-to-r from-orange-600 to-orange-800 hover:from-orange-700 hover:to-orange-900 text-white shadow-lg"
                  >
                    <Download className="w-5 h-5 mr-2" />
                    Open in VLC Player
                  </Button>
                </a>
              )}

              <Button
                variant="outline"
                size="sm"
                onClick={() => setVlcNotInstalled(true)}
                className="w-full border-orange-500/30 text-gray-400 hover:text-white hover:bg-orange-500/20 mb-4"
              >
                VLC Not Installed? Get VLC
              </Button>
            </>
          )}

          <Button
            variant="ghost"
            onClick={() => navigate(-1)}
            className="w-full text-orange-300 hover:text-white hover:bg-orange-500/20"
          >
            <ArrowLeft className="w-4 h-4 mr-2" />
            Go Back
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-black flex flex-col">
      <div className="p-4 md:p-8">
        <div className="flex justify-between items-center">
          <Button
            variant="ghost"
            onClick={() => navigate(-1)}
            className="mb-6 text-orange-300 hover:text-white hover:bg-orange-500/20"
          >
            <ArrowLeft className="w-4 h-4 mr-2" />
            Back
          </Button>

          <div className="flex gap-2 flex-wrap">
            {castAvailable && !casting && !deviceIsIOS && (
              <Button
                variant="outline"
                onClick={handleCast}
                className="mb-6 border-orange-500/30 hover:bg-orange-500/20 text-orange-300"
                title="Cast to TV"
              >
                <Cast className="w-5 h-5 mr-2" />
                Cast to TV
              </Button>
            )}
            {casting && (
              <Button
                variant="outline"
                onClick={() => {
                  if (window.chrome && window.chrome.cast) {
                    const session = window.chrome.cast.framework?.CastContext?.getInstance()?.getCurrentSession();
                    if (session) {
                      session.endSession(true);
                      setCasting(false);
                      if (playerRef.current && playerRef.current.play) {
                        playerRef.current.play().catch(e => console.log('Could not resume local playback:', e));
                      }
                    }
                  }
                }}
                className="mb-6 border-green-500/30 bg-green-500/20 text-green-300"
                title="Stop casting"
              >
                <Cast className="w-5 h-5 mr-2" />
                Casting - Tap to Stop
              </Button>
            )}
          </div>
        </div>

        <div className="flex flex-col md:flex-row justify-between items-start mb-4">
            <h1 className="text-2xl md:text-3xl font-bold text-white mb-2">{channelName}</h1>
        </div>
      </div>

      <div className="flex-grow flex items-center justify-center p-4">
        <div data-vjs-player className="w-full max-w-7xl aspect-video bg-black">
          <video
            ref={videoRef}
            className={deviceIsIOS && isHLS ? "w-full h-full" : "video-js vjs-big-play-centered w-full h-full"}
            playsInline
            autoPlay
          />
        </div>
      </div>
    </div>
  );
}