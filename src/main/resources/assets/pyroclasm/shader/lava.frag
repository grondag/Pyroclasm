#version 120

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

// mininums and sizes for basalt texture
uniform vec4 u_basaltTexSpec;
// mininums and sizes for lava texture
uniform vec4 u_lavaTexSpec;

void main()
{

    vec2 uvRel = (v_texcoord_0 - u_basaltTexSpec.st) / u_basaltTexSpec.pq;
    vec2 uvAlpa = uvRel * u_lavaTexSpec.pq + u_lavaTexSpec.st;
    vec4 mapColor = texture2D(u_textures, uvAlpa);

    // map texture channels are as follows
    // r = broad glow
    // g = cracks with glow
    // b = spotty cracks with glow
    // a = perlin noise


    float i = mapColor.b * smootherstep(0.0, 0.25, v_color_0.a);

    i = max(i, mapColor.a * smootherstep(0.15, 0.45, v_color_0.a));

    i = max(i, mapColor.g * smootherstep(0.25, 0.5, v_color_0.a));

	float a = max(0.0, v_color_0.a * 2.0 - 1.0);
	i = max(i, a * smootherstep(0.65 - 0.65 * a * a, 0.85, mapColor.r));

    // ax + bxx + cxxx
    // x(a + bx + cxx)
    // x(a +x(b + cx))
    float kelvin = 600 + v_color_0.a * i * i * mapColor.r * (200.0 + mapColor.r * ( 800.0 + mapColor.r * 4000.0));

    // subtle, small-scale animation of temperature
	kelvin *= (0.9 + tnoise(uvRel * 512.0, u_time * 8.0) * 0.2);

    // shifting the blue curve out a tad - looks better
    vec4 hotColor = vec4(1.0, green(kelvin), blue(kelvin - 1500.0), 1.0);

    vec4 baseColor = diffuseColor();
    gl_FragColor = fog(mix(vec4(baseColor.rgb, 1.0), hotColor, smootherstep(0.0, 0.95, i)));
}


