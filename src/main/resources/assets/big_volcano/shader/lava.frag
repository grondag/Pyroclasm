#version 120

uniform sampler2D tex;
uniform int time;

void main()
{
	vec4 texColor = texture2D(tex, vec2(gl_TexCoord[0]));
	vec4 baseColor = texColor * gl_Color;
	vec4 hotColor = vec4(1.0, 0.0, 0.0, 1.0);
    //gl_FragColor = texture2D(tex, vec2(gl_TexCoord[0])) * gl_Color * vec4(1.0, 1.0, 1.0, alpha);
	gl_FragColor = mix(hotColor, baseColor, texColor.w);
}
