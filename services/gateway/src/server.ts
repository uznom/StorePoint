import { buildGatewayService } from "./index.js";

const port = Number(process.env.GATEWAY_PORT ?? 4000);
const { gateway } = buildGatewayService();

gateway.listen({ port, host: "0.0.0.0" }).then((address) => {
  console.log(`🚀 StorePoint v2 API Gateway & matraic/m3e Portal running at ${address}`);
});
