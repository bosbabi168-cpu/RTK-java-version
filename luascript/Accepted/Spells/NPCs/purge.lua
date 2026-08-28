purge = {
	cast = function(player)
		player:removeDuras(venoms)
		player:sendMinitext("Kau merapal Purge.")

		player:sendAction(6, 35)

		player:playSound(10)
		player:sendAnimation(10)
	end
}
