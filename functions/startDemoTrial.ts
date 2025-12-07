import { createClientFromRequest } from 'npm:@base44/sdk@0.8.4';

Deno.serve(async (req) => {
  try {
    const base44 = createClientFromRequest(req);
    
    // Get client IP from headers
    const forwarded = req.headers.get('x-forwarded-for');
    const ip = forwarded ? forwarded.split(',')[0].trim() : 
               req.headers.get('x-real-ip') || 
               'unknown';

    // Check if this IP already has a trial
    const existingTrials = await base44.asServiceRole.entities.DemoTrial.filter(
      { ip_address: ip },
      '-created_date',
      1
    );

    if (existingTrials.length > 0) {
      return Response.json({
        success: false,
        error: 'This IP has already used the demo trial'
      }, { status: 400 });
    }

    // Create new trial
    const now = new Date();
    const newTrial = await base44.asServiceRole.entities.DemoTrial.create({
      ip_address: ip,
      trial_start_time: now.toISOString(),
      trial_expired: false
    });

    return Response.json({
      success: true,
      trial_id: newTrial.id,
      remaining_seconds: 3600
    });

  } catch (error) {
    console.error('Start demo trial error:', error);
    return Response.json({ 
      success: false,
      error: error.message
    }, { status: 500 });
  }
});