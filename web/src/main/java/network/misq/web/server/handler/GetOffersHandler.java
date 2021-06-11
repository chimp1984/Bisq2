package network.misq.web.server.handler;


import network.misq.api.Api;
import network.misq.presentation.offer.OfferEntity;
import network.misq.web.json.JsonTransform;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.util.List;


public class GetOffersHandler extends AbstractHandler implements Handler {

    public GetOffersHandler(JsonTransform jsonTransform) {
        super(jsonTransform);
    }

    @Override
    public void handle(Context ctx) {
        Api api = ctx.get(Api.class);
        List<OfferEntity> offers = api.getOfferEntities();
        ctx.render(toJson("offers", offers));
    }
}
