package io.niceseason.gulimall.order.web;

import io.niceseason.common.exception.NoStockException;
import io.niceseason.common.utils.PageUtils;
import io.niceseason.gulimall.order.service.OrderService;
import io.niceseason.gulimall.order.vo.OrderConfirmVo;
import io.niceseason.gulimall.order.vo.OrderSubmitVo;
import io.niceseason.gulimall.order.vo.SubmitOrderResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
public class OrderWebController {
    @Autowired
    private OrderService orderService;

    @GetMapping("/{page}/order.html")
    public String toPage(@PathVariable("page") String page) {
        return page;
    }

//    数据获取
//    查询购物项、库存和收货地址都要调用远程服务，串行会浪费大量时间，因此我们使用CompletableFuture进行异步编排
//    可能由于延迟，订单提交按钮可能被点击多次，为了防止重复提交的问题，我们在返回订单确认页时，在redis中生成一个随机的令牌，过期时间为30min，提交的订单会携带这个令牌，我们将会在订单提交的处理页面核验此令牌
    @RequestMapping("/toTrade")
    public String toTrade(Model model) {
        OrderConfirmVo confirmVo = orderService.confirmOrder();
        model.addAttribute("confirmOrder", confirmVo);
        return "confirm";
    }

//    提交订单
//    提交订单成功，则携带返回数据转发至支付页面
//    提交订单失败，则携带错误信息重定向至确认页
    @RequestMapping("/submitOrder")
    public String submitOrder(OrderSubmitVo submitVo, Model model, RedirectAttributes attributes) {
        try{
            SubmitOrderResponseVo responseVo = orderService.submitOrder(submitVo);
            Integer code = responseVo.getCode();
            if (code == 0){
                model.addAttribute("order", responseVo.getOrder());
                return "pay";
            }else {
                String msg = "下单失败;";
                switch (code) {
                    case 1:
                        msg += "防重令牌校验失败";
                        break;
                    case 2:
                        msg += "商品价格发生变化";
                        break;
                }
                attributes.addFlashAttribute("msg", msg);
                return "redirect:http://order.gulimall.com/toTrade";
            }
        }catch (Exception e){
            if (e instanceof NoStockException){
                String msg = "下单失败，商品无库存";
                attributes.addFlashAttribute("msg", msg);
            }
            return "redirect:http://order.gulimall.com/toTrade";
        }
    }

    /**
     * 获取当前用户的所有订单
     * @return
     */
    @RequestMapping("/memberOrder.html")
    public String memberOrder(@RequestParam(value = "pageNum", required = false, defaultValue = "0") Integer pageNum,
                         Model model){
        Map<String, Object> params = new HashMap<>();
        params.put("page", pageNum.toString());
        //分页查询当前用户的所有订单及对应订单项
        PageUtils page = orderService.getMemberOrderPage(params);
        model.addAttribute("pageUtil", page);
        //返回至订单详情页
        return "list";
    }

}
