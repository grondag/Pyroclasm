#version 120

uniform sampler2D texture;
uniform sampler2D lightMap;
uniform float time;
varying vec4 light;
uniform float uMin;
uniform float vMin;
uniform float uvSize;

//  Patricio Gonzalez, The Book of Shaders
// https://thebookofshaders.com/
vec2 random2(vec2 st)
{
    st = vec2( dot(st,vec2(127.1,311.7)),
              dot(st,vec2(269.5,183.3)) );
    return -1.0 + 2.0*fract(sin(st)*43758.5453123);
}

float random (vec2 st)
{
    return fract(sin(dot(st.xy,
                         vec2(12.9898,78.233)))*
        43758.5453123);
}

// 2D Noise based on Morgan McGuire @morgan3d
// https://www.shadertoy.com/view/4dS3Wd
float noise (in vec2 st)
{
    vec2 i = floor(st);
    vec2 f = fract(st);

    // Four corners in 2D of a tile
    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));

    // Smooth Interpolation

    // Cubic Hermine Curve.  Same as SmoothStep()
    vec2 u = f*f*(3.0-2.0*f);
    // u = smoothstep(0.,1.,f);

    // Mix 4 coorners percentages
    return mix(a, b, u.x) +
            (c - a)* u.y * (1.0 - u.x) +
            (d - b) * u.x * u.y;
}

float noise(vec2 st, float period)
{
    return noise(st * period);
    // return random(st);
}

mat2 makem2(in float theta){float c = cos(theta);float s = sin(theta);return mat2(c,-s,s,c);}

mat2 m2 = mat2( 0.80,  0.60, -0.60,  0.80 );

float grid(vec2 p)
{
	float s = sin(p.x)*cos(p.y);
	return s;
}

float flow(in vec2 p)
{
	float z=2.;
	float rz = 0.;
	float flowTime = time * 0.005;
	vec2 bp = p;
	for (float i= 1.;i < 7.;i++ )
	{
		bp += flowTime*1.5;
		vec2 gr = vec2(grid(p*3.-flowTime*2.),grid(p*3.+4.-flowTime*2.))*0.4;
		gr = normalize(gr)*0.4;
		gr *= makem2((p.x+p.y)*.3+flowTime*10.);
		p += gr*0.5;

		rz+= (sin(noise(p*.01, 256.0)*8.)*0.5+0.5) /z;

		p = mix(bp,p,.5);
		z *= 1.7;
		p *= 2.5;
		p*=m2;
		bp *= 2.5;
		bp*=m2;
	}
	return rz;
}
//float rand(vec2 co)
//{
//    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
//}

// Value Noise by Inigo Quilez - iq/2013
// https://www.shadertoy.com/view/lsf3WH
//float noise(vec2 st)
//{
//    vec2 i = floor(st);
//    vec2 f = fract(st);
//
//    vec2 u = f*f*(3.0-2.0*f);
//
//    return mix( mix( dot( random2(i + vec2(0.0,0.0) ), f - vec2(0.0,0.0) ),
//                     dot( random2(i + vec2(1.0,0.0) ), f - vec2(1.0,0.0) ), u.x),
//                mix( dot( random2(i + vec2(0.0,1.0) ), f - vec2(0.0,1.0) ),
//                     dot( random2(i + vec2(1.0,1.0) ), f - vec2(1.0,1.0) ), u.x), u.y);
//}

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
	// texture for hot blocks contains...
	// rgb = basalt surface
	// a = crack gradient

	vec2 uv = vec2(gl_TexCoord[0]);
	vec4 texColor = texture2D(texture, uv);
	float a = crustAlpha(texColor.a, gl_Color.w);

	if(a == 0)
	{
		vec2 seed = vec2(uv.s - uMin, uv.t - vMin) / uvSize;
		float n = 0.2 / flow(seed);
		gl_FragColor = vec4(1.0, min(1.0, green(gl_Color.a) + n), 0.0, 1.0);
	}
	else
	{
		vec4 baseColor = vec4(texColor.rgb * gl_Color.rgb * light.rgb, 1.0);
//		if(a > 0.8)
//			gl_FragColor = baseColor;
//		else
//		{
			vec4 hotColor = vec4(1.0, green(gl_Color.a) * 0.6, 0.0, 1.0);
			gl_FragColor = mix(hotColor, baseColor, a);
//		}
	}
}


