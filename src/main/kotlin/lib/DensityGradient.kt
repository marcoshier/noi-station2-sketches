package lib

import org.openrndr.draw.Filter1to1
import org.openrndr.draw.filterShaderFromCode

class DensityGradient: Filter1to1(filterShaderFromCode("""
    
    uniform sampler2D tex0;
    in vec2 v_texCoord0;
    out vec4 o_color;
    
    void main() {
        vec2 step = 1.0 / vec2(textureSize(tex0, 0));
        float c = texture(tex0, v_texCoord0).r;
        float w = texture(tex0, v_texCoord0 + step * vec2(-1.0, 0.0)).r;
        float e = texture(tex0, v_texCoord0 + step * vec2(1.0, 0.0)).r;
        float s = texture(tex0, v_texCoord0 + step * vec2(0.0, -1.0)).r;
        float n = texture(tex0, v_texCoord0 + step * vec2(0.0, 1.0)).r;
        
        float dx = (e - w);
        float dy = (n - s);
        
        vec2 g = vec2(dx, dy) * c * 100.0;
        
        if (length(g) > 0.0) {
            g = normalize(g);
        }
        g *= c;

        o_color = vec4(c, g, 1.0);
    }
    
""".trimIndent(),"density-gradient")) {
}