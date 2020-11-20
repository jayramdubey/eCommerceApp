package com.sv.ecommerceapp.cartmicroservice.service;

import com.sv.ecommerceapp.cartmicroservice.exceptions.QuantityUnavailableException;
import com.sv.ecommerceapp.cartmicroservice.model.Cart;
import com.sv.ecommerceapp.cartmicroservice.model.Item;
import com.sv.ecommerceapp.cartmicroservice.proxy.CatalogueProxy;
import com.sv.ecommerceapp.cartmicroservice.repository.CartRepository;

import com.sv.ecommerceapp.cartmicroservice.utilities.CartUtilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CartServiceImpl implements CartService {

	@Autowired
	CartRepository cartRepository;

	@Autowired
	CatalogueProxy catalogueProxy;

	public static final Long CART_ID = 0001L;

	@Override
	public Cart addItemToCart(Long productId, Integer quantity) {
		Item item = catalogueProxy.retriveProductById(productId);
		if(checkOutOfStock(productId,quantity)){
			throw new QuantityUnavailableException(
					"Available stock for this product is "+item.getQuantity()+". Please reduce it!");
		}
		item.setQuantity(quantity);
		if (checkIfCartExist()) {
			return mergeToCart(item, quantity);
		} else {
			return createCart(item, quantity);
		}
	}

	private boolean checkOutOfStock(Long productId, Integer quantity) {
		Item item = catalogueProxy.retriveProductById(productId);
		if (item.getQuantity() < quantity)
           return true;
		else
			return false;

	}


	private Cart createCart(Item item, Integer quantity) {
		Cart cart = new Cart();
		cart.setCartId(CART_ID);
		cart.addItems(item);
		cart.setTotalAmount(CartUtilities.getTotalForCart(cart));
		return cartRepository.save(cart);
	}

	private Cart mergeToCart(Item item, Integer quantity) {
		Optional<Cart> retrievedCart = cartRepository.findById(CART_ID);
		List<Item> retrievedItems = retrievedCart.get().getItems();
		if (retrievedItems.contains(item)) {
			retrievedItems.forEach(it -> {
				if (it.getId().equals(item.getId())) {
					it.setQuantity(it.getQuantity() + quantity);
				}
			});
		} else {
			retrievedItems.add(item);
		}
		// Workaround to fix concurrent modification exception
		return cartRepository.save(getUpdatedCart(retrievedItems));

	}

	private Cart getUpdatedCart(List<Item> retrievedItems) {
		cartRepository.deleteById(CART_ID);
		Cart cart = new Cart();
		cart.setCartId(CART_ID);
		retrievedItems.forEach(it -> {
			cart.addItems(it);
		});
		cart.setTotalAmount(CartUtilities.getTotalForCart(cart));
		return cart;
	}

	private boolean checkIfCartExist() {
		return cartRepository.existsById(CART_ID);
	}

	@Override
	public Cart retrieveCart() {
		return cartRepository.findById(CART_ID).get();
	}

	@Override
	public void clearCart() {
		cartRepository.deleteById(CART_ID);
	}

	@Override
	public void removeItem(Long productId) {
		Optional<Cart> retrievedCart = cartRepository.findById(CART_ID);
		cartRepository.deleteById(CART_ID);
		List<Item> retrievedItems = retrievedCart.get().getItems();
		retrievedItems.removeIf(item -> item.getId().equals(productId));
		Cart cart = new Cart();
		cart.setCartId(CART_ID);
		retrievedItems.forEach(it -> {
			cart.addItems(it);
		});
		cart.setTotalAmount(CartUtilities.getTotalForCart(cart));
		cartRepository.save(cart);
	}

	@Override
	public Cart updateItemQuantity(Long productId, int quantity) {
		Optional<Cart> retrievedCart = cartRepository.findById(CART_ID);
		List<Item> retrievedItems = retrievedCart.get().getItems();
			retrievedItems.forEach(it -> {
				if (it.getId().equals(productId)) {
					if (!checkOutOfStock(productId,(quantity-it.getQuantity())) ) {
						it.setQuantity(quantity);
					}
					else{
						throw new QuantityUnavailableException(
								"Stock unavailable. Please reduce quantity!");
					}
				}
			});
		return cartRepository.save(getUpdatedCart(retrievedItems));
	}
}
