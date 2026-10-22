package cat.narezany.margyt;

/** In-app acrylic: a lightly softened backdrop, restrained tint and fixed fine grain. */
final class FluentShader {
    private FluentShader() {}
    static final String ACRYLIC = """
        uniform shader content;
        uniform float2 size;
        uniform float2 radii;
        uniform float4 tint;
        uniform float density;
        uniform float pressed;
        half4 main(float2 p) {
            float radius = p.y < size.y * .5 ? radii.x : radii.y;
            float2 q = abs(p - size * .5) - size * .5 + radius;
            float d = length(max(q, float2(0))) + min(max(q.x,q.y),0.) - radius;
            float coverage = 1. - smoothstep(-.7,.7,d);
            if (coverage <= .001) return half4(0);
            float3 backdrop = content.eval(p).rgb;
            // Acrylic's luminosity layer keeps video colour from washing out the neutral surface.
            float luma = dot(backdrop,float3(.2126,.7152,.0722));
            backdrop = mix(float3(luma),backdrop,.9);
            float3 colour = mix(backdrop,tint.rgb,tint.a);
            float2 cell = floor(p / max(1.,density));
            float grain = fract(sin(dot(cell,float2(12.9898,78.233))) * 43758.5453) - .5;
            colour += grain * .003 + pressed * .018;
            float edge = smoothstep(-max(1.,density*.4),0.,d);
            colour = mix(colour,float3(1.),edge*.075);
            return half4(half3(clamp(colour,0.,1.)*coverage),half(coverage));
        }
        """;
}
