/* TikTok MI landing v2 — GSAP motion, counters, cursor, live version. Degrades gracefully. */
(function () {
  "use strict";

  var year = document.getElementById("year");
  if (year) year.textContent = new Date().getFullYear();

  // Live version badge + download buttons: what version.json says, with static fallbacks.
  // Every [data-download] link is rewritten to the fresh apk URL, so one tap
  // starts installing the newest release instead of opening the releases page.
  var badge = document.getElementById("version");
  if (window.fetch) {
    fetch("https://raw.githubusercontent.com/fuckramochka/tiktokmi/main/version.json", { cache: "no-store" })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (j) {
        if (!j) return;
        if (badge && j.version) badge.textContent = "v" + String(j.version).replace(/^v/i, "");
        if (j.url) {
          document.querySelectorAll("a[data-download]").forEach(function (a) {
            a.setAttribute("href", j.url);
          });
        }
      })
      .catch(function () {});
  }

  // Preloader out.
  function loaderOut() {
    var loader = document.getElementById("loader");
    if (loader) loader.classList.add("done");
  }

  // Counters: 24 countries, 0 ads, 100%.
  function counters(scope) {
    (scope.querySelectorAll("[data-count]") || []).forEach(function (el) {
      var target = parseInt(el.getAttribute("data-count"), 10) || 0;
      if (!window.gsap) { el.textContent = target; return; }
      var obj = { v: 0 };
      gsap.to(obj, {
        v: target, duration: 1.6, ease: "power2.out",
        scrollTrigger: { trigger: el, start: "top 88%", once: true },
        onUpdate: function () { el.textContent = Math.round(obj.v); }
      });
    });
  }

  // Magnetic buttons (fine pointers only).
  function magnetic() {
    if (!window.gsap || !window.matchMedia("(hover: hover)").matches) return;
    document.querySelectorAll(".magnetic").forEach(function (el) {
      el.addEventListener("mousemove", function (e) {
        var r = el.getBoundingClientRect();
        gsap.to(el, { x: (e.clientX - r.left - r.width / 2) * 0.22, y: (e.clientY - r.top - r.height / 2) * 0.28, duration: 0.3 });
      });
      el.addEventListener("mouseleave", function () {
        gsap.to(el, { x: 0, y: 0, duration: 0.5, ease: "elastic.out(1,0.4)" });
      });
    });
  }

  // Phone tilt following the mouse.
  function tilt() {
    var phone = document.getElementById("phone");
    if (!phone || !window.gsap || !window.matchMedia("(hover: hover)").matches) return;
    var rx = gsap.quickTo(phone, "rotationX", { duration: 0.6, ease: "power3" });
    var ry = gsap.quickTo(phone, "rotationY", { duration: 0.6, ease: "power3" });
    gsap.set(phone, { transformPerspective: 900 });
    window.addEventListener("mousemove", function (e) {
      ry(((e.clientX / window.innerWidth) - 0.5) * 14);
      rx(-((e.clientY / window.innerHeight) - 0.5) * 10);
    });
  }

  // Custom cursor.
  function cursor() {
    if (!window.matchMedia("(hover: hover)").matches) return;
    var dot = document.querySelector(".cursor");
    var ring = document.querySelector(".cursor-ring");
    if (!dot || !ring || !window.gsap) return;
    var x = gsap.quickTo(ring, "x", { duration: 0.35, ease: "power3" });
    var y = gsap.quickTo(ring, "y", { duration: 0.35, ease: "power3" });
    window.addEventListener("mousemove", function (e) {
      gsap.set(dot, { x: e.clientX, y: e.clientY });
      x(e.clientX); y(e.clientY);
    });
    document.querySelectorAll("a, button, summary").forEach(function (el) {
      el.addEventListener("mouseenter", function () { ring.classList.add("hot"); });
      el.addEventListener("mouseleave", function () { ring.classList.remove("hot"); });
    });
  }

  // Entrance + scroll motion.
  function motion() {
    if (!window.gsap) { loaderOut(); return; }
    gsap.registerPlugin(ScrollTrigger);

    gsap.fromTo("[data-hero]",
      { y: 44, opacity: 0 },
      { y: 0, opacity: 1, duration: 1.05, ease: "power3.out", stagger: 0.1, delay: 0.55 });

    gsap.utils.toArray("[data-reveal]").forEach(function (el) {
      gsap.fromTo(el, { y: 36, opacity: 0 }, {
        y: 0, opacity: 1, duration: 0.9, ease: "power3.out",
        scrollTrigger: { trigger: el, start: "top 88%", once: true }
      });
    });

    // Orbs drift with the scroll.
    gsap.to(".orb-pink", { yPercent: 24, ease: "none", scrollTrigger: { trigger: "main", start: "top top", end: "bottom bottom", scrub: 1 } });
    gsap.to(".orb-cyan", { yPercent: -20, ease: "none", scrollTrigger: { trigger: "main", start: "top top", end: "bottom bottom", scrub: 1 } });

    window.addEventListener("load", loaderOut);
    setTimeout(loaderOut, 2600);
  }

  counters(document);
  magnetic();
  tilt();
  cursor();
  motion();
})();
