#version 120

// mininums and sizes for basalt texture
uniform vec4 u_basaltTexSpec;
// mininums and sizes for lava texture
uniform vec4 u_lavaTexSpec;

varying vec4 light;

void main()
{
	setupVertex();
//    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
//    gl_TexCoord[0] = gl_MultiTexCoord0;
//    gl_TexCoord[1] = gl_MultiTexCoord1;
//
//	// first is block light, second is sky light
//	light = texture2D(lightMap, vec2((gl_MultiTexCoord1.x + 8.0) / 255.0, (gl_MultiTexCoord1.y + 8.0) / 255.0));
//    gl_FrontColor = gl_Color;
}
