import { createClientFromRequest } from 'npm:@base44/sdk@0.8.4';

Deno.serve(async (req) => {
  try {
    const base44 = createClientFromRequest(req);
    
    // Get client IP from headers
    const forwarded = req.headers.get('x-forwarded-for');
    const ip = forwarded ? forwarded.split(',')[0].trim() : 
               req.headers.get('x-real-ip') || 
               'unknown';

    // Check if this IP has an active trial
    const existingTrials = await base44.asServiceRole.entities.DemoTrial.filter(
      { ip_address: ip },
      '-created_date',
      1
    );

    const now = new Date();
    const oneHourInMs = 60 * 60 * 1000;

    if (existingTrials.length > 0) {
      const trial = existingTrials[0];
      const trialStart = new Date(trial.trial_start_time);
      const elapsed = now - trialStart;
      const remaining = oneHourInMs - elapsed;

      if (remaining <= 0 || trial.trial_expired) {
        // Trial expired or used - mark as expired
        if (!trial.trial_expired) {
          await base44.asServiceRole.entities.DemoTrial.update(trial.id, {
            trial_expired: true
          });
        }
        
        return Response.json({
          trial_active: false,
          trial_expired: true,
          trial_used: true,
          remaining_seconds: 0
        });
      }

      // Trial still active
      return Response.json({
        trial_active: true,
        trial_expired: false,
        trial_used: false,
        remaining_seconds: Math.floor(remaining / 1000),
        trial_id: trial.id
      });
    }

    // No trial exists for this IP - can create new one
    return Response.json({
      trial_active: false,
      trial_expired: false,
      trial_used: false,
      can_start_trial: true,
      remaining_seconds: 0
    });

  } catch (error) {
    console.error('Demo trial check error:', error);
    return Response.json({ 
      error: error.message,
      trial_active: false 
    }, { status: 500 });
  }
});