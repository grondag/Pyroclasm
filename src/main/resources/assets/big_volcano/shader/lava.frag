#version 120

uniform sampler2D texture;
uniform sampler2D lightMap;
//uniform int time;
varying vec4 light;

// Maps input alpha to kelvin degrees and then estimates green
// color component for black body radiation at that temperature.
// For range we are using, can assume red component is 1.0 and blue is 0.0.
float green(float alpha)
{
    const float a = -155.25485562709179;
    const float b = -0.44596950469579133;
    const float c = 104.49216199393888;
    float x = ((800 + alpha * 800.0) / 100.0) - 2;
    return (a + b * x + c * log(x)) / 255.0;
}

void main()
{
	vec4 texColor = texture2D(texture, vec2(gl_TexCoord[0]));
	vec3 baseColor = texColor.rgb * gl_Color.rgb;
	vec4 hotColor = vec4(1.0, green(gl_Color.w), 0.0, 1.0);
//	vec2 lc = vec2(gl_TexCoord[1]);
	// first is block light, second is sky light
//	vec4 light = texture2D(lightMap, vec2((lc.x + 8) / 255.0, (lc.y + 8.0) / 255.0));
	gl_FragColor = vec4(gl_Color.rgb * light.rgb, 1.0);
//	gl_FragColor = vec4(lc.y / 255.0, lc.y / 255.0, lc.y / 255.0, 1.0);
//	gl_FragColor = mix(hotColor, combined, texColor.w); // min(1.0, texColor.w * (2.0 - gl_Color.w)));
}


