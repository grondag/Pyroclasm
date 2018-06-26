#version 120

uniform sampler2D texture;
uniform sampler2D lightMap;
//uniform int time;
varying vec4 light;

// Maps input alpha to kelvin degrees and then estimates green
// color component for black body radiation at that temperature.
// For range we are using, can assume red component is 1.0 and blue is 0.0.
float green(float heatAlpha)
{
    const float a = -155.25485562709179;
    const float b = -0.44596950469579133;
    const float c = 104.49216199393888;
    float x = ((600 + heatAlpha * 600.0) / 100.0) - 2;
    return (a + b * x + c * log(x)) / 255.0;
}

/**
 *
 */
float crustAlpha(float translucency, float heatAlpha)
{
	// hottest
	// t = 0 -> 0
	// t = .5 -> .3
	// t = .9 -> 1

	// coolest
	// t = .98 -> 1
	// t = .5 -> .1
//	float top = 1 - heatAlpha * 0.1;
	float bottom = 1 - sqrt(heatAlpha);
	float t = smoothstep(bottom, 1, translucency);
	return t > 0.5 ? 0 : 1 - t;

//	float coolness = 1 - heatAlpha;
//	return 1.0 - translucency * (1 - coolness * coolness * coolness);
//	return (1 - texAlpha) * 0.98 + 0.02;
//	float translucency = 1 - texAlpha;
//	return 1.0 - texAlpha * (1 - coolness * coolness * coolness);

//	if(heatAlpha == 0.0)
//		return 1.0;
//	else if(heatAlpha >= texAlpha)
//		return 0.0;
//	else
//	{
//		return clamp(1.0 - translucency * (1 - coolness * coolness), 0.0, 1.0);
//	}
//		return clamp(1.0 - (1.0 - texAlpha) * heatAlpha * heatAlpha * 4.0, 0.0, 1.0);

}

void main()
{

	// texture for hot blocks contains four images
	// r = basalt surface
	// g = glow gradient
	// b = crack gradient
	// a = plasma
	vec4 texColor = texture2D(texture, vec2(gl_TexCoord[0]));
	float a = crustAlpha(texColor.g, gl_Color.w);

	if(a == 0)
	{
		gl_FragColor = vec4(1.0, green(gl_Color.a) + texColor.a * gl_Color.a / 2.0, 0.0, 1.0);
	}
	else
	{
		vec4 baseColor = vec4(texColor.rrr * gl_Color.rgb * light.rgb, 1.0);
//		if(a > 0.9)
			gl_FragColor = baseColor;
//		else
//		{
//			vec4 hotColor = vec4(1.0, green(gl_Color.a) * 0.6, 0.0, 1.0);
//			gl_FragColor = mix(hotColor, baseColor, a);
//		}
	}
}


