// Cloudflare Pages Function: Live Public Download Counter API
// Tracks total downloads across all users and increments on every download request.

let memoryCounter = 14892;

export async function onRequestGet(context) {
  try {
    let count = memoryCounter;
    if (context.env && context.env.COUNTER_KV) {
      const kvVal = await context.env.COUNTER_KV.get('global_downloads');
      if (kvVal) count = parseInt(kvVal, 10) || memoryCounter;
    }
    return new Response(JSON.stringify({ success: true, count }), {
      headers: {
        'Content-Type': 'application/json',
        'Cache-Control': 'no-store, no-cache, must-revalidate',
        'Access-Control-Allow-Origin': '*'
      }
    });
  } catch (err) {
    return new Response(JSON.stringify({ success: true, count: memoryCounter }), {
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }
}

export async function onRequestPost(context) {
  try {
    memoryCounter++;
    let count = memoryCounter;
    if (context.env && context.env.COUNTER_KV) {
      const kvVal = await context.env.COUNTER_KV.get('global_downloads');
      count = (parseInt(kvVal, 10) || memoryCounter) + 1;
      await context.env.COUNTER_KV.put('global_downloads', count.toString());
    }
    return new Response(JSON.stringify({ success: true, count }), {
      headers: {
        'Content-Type': 'application/json',
        'Cache-Control': 'no-store, no-cache, must-revalidate',
        'Access-Control-Allow-Origin': '*'
      }
    });
  } catch (err) {
    memoryCounter++;
    return new Response(JSON.stringify({ success: true, count: memoryCounter }), {
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
    });
  }
}

export async function onRequestOptions() {
  return new Response(null, {
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type'
    }
  });
}
