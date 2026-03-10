package com.ipinfo.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProxyData {

    @Builder.Default private boolean vpn     = false;
    @Builder.Default private boolean proxy   = false;
    @Builder.Default private boolean tor     = false;
    @Builder.Default private boolean hosting = false;
    @Builder.Default private String type     = "";
    @Builder.Default private String provider = "";
}
