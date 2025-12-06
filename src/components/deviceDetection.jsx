// Device detection utility for optimizing app performance across different devices

export const detectDevice = () => {
  const userAgent = navigator.userAgent || navigator.vendor || window.opera;
  
  // Detect TV devices
  const isTV = /TV|GoogleTV|SmartTV|AFTT|AFTB|AFTM|AFTRS|AFTS|AFTKA|AFTKMST|AFTSS|AFTEU|AFTR|AFTEU|AFTMM/.test(userAgent) ||
               /CrKey|Roku|PlayStation|Xbox/.test(userAgent) ||
               window.matchMedia('(pointer: coarse) and (min-width: 800px)').matches;
  
  // Detect mobile
  const isMobile = /iPhone|iPad|iPod|Android|webOS|BlackBerry|IEMobile|Opera Mini/i.test(userAgent) && 
                   !isTV;
  
  // Detect tablet
  const isTablet = (/iPad|Android/i.test(userAgent) && !isTV) && 
                   window.matchMedia('(min-width: 768px)').matches;
  
  // Detect desktop
  const isDesktop = !isMobile && !isTablet && !isTV;
  
  return {
    isTV,
    isMobile,
    isTablet,
    isDesktop,
    deviceType: isTV ? 'tv' : isMobile ? 'mobile' : isTablet ? 'tablet' : 'desktop'
  };
};

export const getPerformanceConfig = (deviceType) => {
  const configs = {
    tv: {
      enableAnimations: false,
      enableBlur: false,
      enableGradients: true,
      imageQuality: 'medium',
      maxGridColumns: 6,
      transitionDuration: 150,
      focusMode: true,
      largerHitTargets: true
    },
    mobile: {
      enableAnimations: true,
      enableBlur: true,
      enableGradients: true,
      imageQuality: 'medium',
      maxGridColumns: 2,
      transitionDuration: 200,
      focusMode: false,
      largerHitTargets: false
    },
    tablet: {
      enableAnimations: true,
      enableBlur: true,
      enableGradients: true,
      imageQuality: 'high',
      maxGridColumns: 4,
      transitionDuration: 200,
      focusMode: false,
      largerHitTargets: false
    },
    desktop: {
      enableAnimations: true,
      enableBlur: true,
      enableGradients: true,
      imageQuality: 'high',
      maxGridColumns: 6,
      transitionDuration: 200,
      focusMode: false,
      largerHitTargets: false
    }
  };
  
  return configs[deviceType] || configs.desktop;
};