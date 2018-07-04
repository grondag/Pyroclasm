#version 120

uniform float time;
uniform vec4 uvBasalt;
uniform vec4 uvMap;
uniform sampler2D texture;
uniform sampler2D lightMap;
varying vec4 light;

// from somewhere on the Internet...
float random (vec2 st)
{
    return fract(sin(dot(st.xy,
                         vec2(12.9898,78.233)))*
        43758.5453123);
}

// Ken Perlin's improved smoothstep
float smootherstep(float edge0, float edge1, float x)
{
  // Scale, and clamp x to 0..1 range
  x = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
  // Evaluate polynomial
  return x * x * x * (x * (x * 6 - 15) + 10);
}

// Based in part on 2D Noise by Morgan McGuire @morgan3d
// https://www.shadertoy.com/view/4dS3Wd
float tnoise (in vec2 st, float t)
{
    vec2 i = floor(st);
    vec2 f = fract(st);

    // Compute values for four corners
    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));

    a =  0.5 + sin((0.5 + a) * t) * 0.5;
    b =  0.5 + sin((0.5 + b) * t) * 0.5;
    c =  0.5 + sin((0.5 + c) * t) * 0.5;
    d =  0.5 + sin((0.5 + d) * t) * 0.5;

    // Mix 4 corners
    return mix(a, b, f.x) +
            (c - a)* f.y * (1.0 - f.x) +
            (d - b) * f.x * f.y;
}

// Color temperature computations are based on following sources, with much appreciation:
//
// Tanner Helland: How to Convert Temperature (K) to RGB: Algorithm and Sample Code
// http://www.tannerhelland.com/4435/convert-temperature-rgb-algorithm-code/
//
// Neil Bartlett: COLOR TEMPERATURE CONVERSION OF HOMESTAR.IO
// http://www.zombieprototypes.com/?p=210

// Estimates green color component for black body radiation at input temperature.
// For range we are using, can assume red component is 1.0
float green(float kelvin)
{
    const float a = -155.25485562709179;
    const float b = -0.44596950469579133;
    const float c = 104.49216199393888;
    float x = (kelvin / 100.0) - 2;
    return (a + b * x + c * log(x)) / 255.0;
}

// Estimates blue color component for black body radiation at input temperature.
// For range we are using, can assume red component is 1.0
float blue(float kelvin)
{
	if(kelvin < 2000.0) return 0.0;
    const float a = -254.76935184120902;
    const float b = 0.8274096064007395;
    const float c = 115.67994401066147;
    float x = (kelvin / 100.0) - 10.0;
    return (a + b * x + c * log(x)) / 255.0;
}

void main()
{
	vec2 uvTex = vec2(gl_TexCoord[0]);
	vec4 texColor = texture2D(texture, uvTex);
    vec4 baseColor = vec4(texColor.rgb * gl_Color.rgb * light.rgb, 1.0);

    vec2 uvRel = (uvTex - uvBasalt.st) / uvBasalt.pq;
    vec2 uvAlpa = uvRel * uvMap.pq + uvMap.st;
    vec4 mapColor = texture2D(texture, uvAlpa);

    // map texture channels are as follows
    // r = broad glow
    // g = cracks with glow
    // b = spotty cracks with glow
    // a = perlin noise


    float i = mapColor.b * smootherstep(0.0, 0.25, gl_Color.a);

    i = max(i, mapColor.a * smootherstep(0.15, 0.45, gl_Color.a));

    i = max(i, mapColor.g * smootherstep(0.25, 0.5, gl_Color.a));

	float a = max(0.0, gl_Color.a * 2.0 - 1.0);
	i = max(i, a * smootherstep(0.65 - 0.65 * a * a, 0.85, mapColor.r));

    // ax + bxx + cxxx
    // x(a + bx + cxx)
    // x(a +x(b + cx))
    float kelvin = 600 + gl_Color.a * i * i * mapColor.r * (200.0 + mapColor.r * ( 800.0 + mapColor.r * 4000.0));

    // subtle, small-scale animation of temperature
	kelvin *= (0.9 + tnoise(uvRel * 512.0, time * 2.0) * 0.2);

    // shifting the blue curve out a tad - looks better
    vec4 hotColor = vec4(1.0, green(kelvin), blue(kelvin - 1500.0), 1.0);

    gl_FragColor = mix(baseColor, hotColor, smootherstep(0.0, 0.95, i));
}


