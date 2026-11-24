/* web/assets/gta6-guides.js
 * Geo-Targeted Hardware Affiliate Engine, Interactive Downloader CTA & Adblock Fallback
 */
(function () {
  'use strict';

  // =========================================================================
  // 1. CENTRAL AFFILIATE CONFIGURATION (Drop your affiliate IDs here anytime)
  // =========================================================================
  window.HK_AFFILIATE_CONFIG = {
    amazonTags: {
      US: 'hkdownloader-20',
      UK: 'hkdownloader21-21',
      DE: 'hkdownloader0e-21',
      CA: 'hkdownloader0c-20',
      AU: 'hkdownloader-22',
      IN: 'hkdownloader-21',
      AE: 'hkdownloader-21',
      PK: 'hkdownloader-20'
    },
    cdkeysAffiliateId: 'hkdownloader',
    instantGamingTag: 'hkdownloader',
    greenManGamingTag: 'hkdownloader'
  };

  // =========================================================================
  // 2. REGIONAL STORE & CURRENCY DEFINITIONS
  // =========================================================================
  const REGIONS = {
    US: {
      code: 'US',
      label: '🇺🇸 United States (USD $)',
      amazonDomain: 'www.amazon.com',
      currency: '$',
      rate: 1.0,
      storeName: 'Amazon US',
      gameStoreName: 'PlayStation / Amazon US'
    },
    UK: {
      code: 'UK',
      label: '🇬🇧 United Kingdom (GBP £)',
      amazonDomain: 'www.amazon.co.uk',
      currency: '£',
      rate: 0.79,
      storeName: 'Amazon UK',
      gameStoreName: 'Amazon UK / GAME'
    },
    DE: {
      code: 'DE',
      label: '🇪🇺 Europe / Germany (EUR €)',
      amazonDomain: 'www.amazon.de',
      currency: '€',
      rate: 0.92,
      storeName: 'Amazon EU',
      gameStoreName: 'Amazon EU / Instant Gaming'
    },
    CA: {
      code: 'CA',
      label: '🇨🇦 Canada (CAD CA$)',
      amazonDomain: 'www.amazon.ca',
      currency: 'CA$',
      rate: 1.38,
      storeName: 'Amazon Canada',
      gameStoreName: 'Amazon CA / Best Buy CA'
    },
    AU: {
      code: 'AU',
      label: '🇦🇺 Australia (AUD A$)',
      amazonDomain: 'www.amazon.com.au',
      currency: 'A$',
      rate: 1.52,
      storeName: 'Amazon AU',
      gameStoreName: 'Amazon AU / JB Hi-Fi'
    },
    IN: {
      code: 'IN',
      label: '🇮🇳 India (INR ₹)',
      amazonDomain: 'www.amazon.in',
      currency: '₹',
      rate: 84.0,
      storeName: 'Amazon India',
      gameStoreName: 'Amazon IN / Games The Shop'
    },
    AE: {
      code: 'AE',
      label: '🇦🇪 UAE & Middle East (AED)',
      amazonDomain: 'www.amazon.ae',
      currency: 'AED ',
      rate: 3.67,
      storeName: 'Amazon UAE',
      gameStoreName: 'Amazon AE / Geekay'
    },
    PK: {
      code: 'PK',
      label: '🇵🇰 Pakistan & Global (PKR / USD)',
      amazonDomain: 'www.amazon.com',
      currency: 'Rs ',
      rate: 278.0,
      storeName: 'Amazon Global / Daraz PK',
      gameStoreName: 'CDKeys / PSN Global'
    }
  };

  function detectUserRegion() {
    const saved = localStorage.getItem('hk_user_region');
    if (saved && REGIONS[saved]) return saved;

    const tz = (Intl.DateTimeFormat().resolvedOptions().timeZone || '').toLowerCase();
    const lang = (navigator.language || '').toLowerCase();

    if (tz.includes('karachi') || lang.endsWith('-pk')) return 'PK';
    if (tz.includes('kolkata') || tz.includes('calcutta') || lang.endsWith('-in')) return 'IN';
    if (tz.includes('dubai') || tz.includes('riyadh') || lang.endsWith('-ae') || lang.endsWith('-sa')) return 'AE';
    if (tz.includes('london') || lang.endsWith('-gb') || lang.endsWith('-uk')) return 'UK';
    if (tz.includes('sydney') || tz.includes('melbourne') || lang.endsWith('-au')) return 'AU';
    if (tz.includes('toronto') || tz.includes('vancouver') || lang.endsWith('-ca')) return 'CA';
    if (tz.includes('berlin') || tz.includes('paris') || tz.includes('madrid') || tz.includes('rome') || tz.includes('amsterdam')) return 'DE';
    return 'US';
  }

  function formatRegionalPrice(usdBase, regionCode) {
    const reg = REGIONS[regionCode] || REGIONS.US;
    const raw = Math.round(usdBase * reg.rate);
    if (regionCode === 'IN' || regionCode === 'PK' || regionCode === 'AE') {
      return `${reg.currency}${raw.toLocaleString()}`;
    }
    return `${reg.currency}${raw.toLocaleString()}`;
  }

  function buildStoreUrl(query, category, regionCode) {
    const reg = REGIONS[regionCode] || REGIONS.US;
    const tag = window.HK_AFFILIATE_CONFIG.amazonTags[regionCode] || window.HK_AFFILIATE_CONFIG.amazonTags.US;
    return `https://${reg.amazonDomain}/s?k=${encodeURIComponent(query)}&tag=${encodeURIComponent(tag)}`;
  }

  function applyGeoToHardwareCards(regionCode) {
    const reg = REGIONS[regionCode] || REGIONS.US;
    localStorage.setItem('hk_user_region', regionCode);

    document.querySelectorAll('.geo-select').forEach(sel => {
      if (sel.value !== regionCode) sel.value = regionCode;
    });

    document.querySelectorAll('.geo-active-badge').forEach(badge => {
      badge.textContent = `Showing prices & stores for: ${reg.label}`;
    });

    document.querySelectorAll('.hw-card[data-query]').forEach(card => {
      const query = card.getAttribute('data-query') || 'GTA 6 PS5 Pro';
      const usdPrice = parseFloat(card.getAttribute('data-usd') || '499');
      const cat = card.getAttribute('data-cat') || 'hardware';

      const priceEl = card.querySelector('.hw-price');
      if (priceEl && !isNaN(usdPrice)) {
        priceEl.textContent = `~${formatRegionalPrice(usdPrice, regionCode)}`;
      }

      const btnEl = card.querySelector('.hw-store-btn');
      if (btnEl) {
        btnEl.href = buildStoreUrl(query, cat, regionCode);
        const sName = cat === 'game' ? reg.gameStoreName : reg.storeName;
        btnEl.innerHTML = `Check Price on ${sName} &rarr;`;
      }
    });
  }

  // =========================================================================
  // 3. INTERACTIVE DOWNLOADER CTA & 1-CLICK TRAILER DOWNLOAD
  // =========================================================================
  function initDownloaderCtas() {
    document.querySelectorAll('.downloader-cta-form').forEach(form => {
      form.addEventListener('submit', function (e) {
        e.preventDefault();
        const input = form.querySelector('.downloader-cta-input');
        const val = (input && input.value ? input.value.trim() : '') || 'https://www.youtube.com/watch?v=QdBZY2fkU-0';
        window.location.href = `/?url=${encodeURIComponent(val)}`;
      });
    });

    document.querySelectorAll('[data-dl-url]').forEach(btn => {
      btn.addEventListener('click', function (e) {
        e.preventDefault();
        const targetUrl = btn.getAttribute('data-dl-url');
        if (targetUrl) {
          window.location.href = `/?url=${encodeURIComponent(targetUrl)}`;
        }
      });
    });
  }

  // =========================================================================
  // 4. ADBLOCK / BRAVE SHIELDS DETECTION & NATIVE HARDWARE FALLBACK
  // =========================================================================
  function initAdblockFallbacks() {
    setTimeout(() => {
      const reg = REGIONS[detectUserRegion()] || REGIONS.US;
      const ps5ProUrl = buildStoreUrl('Sony PlayStation 5 Pro Console 2TB', 'hardware', reg.code);
      const rtxUrl = buildStoreUrl('NVIDIA GeForce RTX 4080 Super 16GB GPU', 'hardware', reg.code);

      // Check Native container
      const nativeContainer = document.getElementById('container-0dc5280b04ec0aeab29bb231530dbf13');
      if (nativeContainer && nativeContainer.children.length === 0 && nativeContainer.offsetHeight < 15) {
        const fb = nativeContainer.closest('.ad-slot-box')?.querySelector('.ad-fallback-banner');
        if (fb) fb.classList.add('visible');
      }

      // Check every ad-slot-box that has a fallback banner
      document.querySelectorAll('.ad-slot-box').forEach(box => {
        const iframe = box.querySelector('iframe');
        const fb = box.querySelector('.ad-fallback-banner');
        if (!fb) return;

        const linkEl = fb.querySelector('.ad-fb-link');
        if (linkEl && !linkEl.getAttribute('href')) {
          linkEl.href = ps5ProUrl;
        }
        const linkEl2 = fb.querySelector('.ad-fb-link-gpu');
        if (linkEl2) {
          linkEl2.href = rtxUrl;
        }
      });
    }, 2200);
  }

  document.addEventListener('DOMContentLoaded', () => {
    const initialRegion = detectUserRegion();
    applyGeoToHardwareCards(initialRegion);

    document.querySelectorAll('.geo-select').forEach(sel => {
      sel.addEventListener('change', (e) => {
        applyGeoToHardwareCards(e.target.value);
      });
    });

    initDownloaderCtas();
  });
})();
