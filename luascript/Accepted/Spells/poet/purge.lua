purge_poet = {
	cast = function(player, target)
		local magic = 30

		if not player:canCast(1, 1, 0) then
			return
		end
		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		target:removeDuras(venoms)
		player:sendMinitext("Kau merapal Purge.")

		player:sendAction(6, 35)
		player.magic = player.magic - magic
		target:playSound(10)
		target:sendAnimation(10)
		target:sendStatus()
		player:sendStatus()
	end,
	requirements = function(player)
		local level = 12
		local items = {Item("snake_meat").id, Item("wine").id, 0}
		local itemAmounts = {10, 1, 100}
		local description = "Removes poison from target"
		return level, items, itemAmounts, description
	end
}

cure_illness_poet = {
	cast = function(player, target)
		local magic = 30

		if not player:canCast(1, 1, 0) then
			return
		end
		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		target:removeDuras(venoms)
		player:sendMinitext("Kau merapal Cure illness.")

		player:sendAction(6, 35)
		player.magic = player.magic - magic
		target:playSound(10)
		target:sendAnimation(70)
		target:sendStatus()
		player:sendStatus()
	end,
	requirements = function(player)
		local level = 12
		local items = {Item("snake_meat").id, Item("wine").id, 0}
		local itemAmounts = {10, 1, 100}
		local description = "Removes poison from target"
		return level, items, itemAmounts, description
	end
}

restore_health_poet = {
	cast = function(player, target)
		local magic = 30

		if not player:canCast(1, 1, 0) then
			return
		end
		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		target:removeDuras(venoms)
		player:sendMinitext("Kau merapal Restore health.")

		player:sendAction(6, 35)
		player.magic = player.magic - magic
		target:playSound(10)
		target:sendAnimation(57)
		target:sendStatus()
		player:sendStatus()
	end,
	requirements = function(player)
		local level = 12
		local items = {Item("snake_meat").id, Item("wine").id, 0}
		local itemAmounts = {10, 1, 100}
		local description = "Removes poison from target"
		return level, items, itemAmounts, description
	end
}

remove_poison_poet = {
	cast = function(player, target)
		local magic = 30

		if not player:canCast(1, 1, 0) then
			return
		end
		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		target:removeDuras(venoms)
		player:sendMinitext("Kau merapal Remove poison.")

		player:sendAction(6, 35)
		player.magic = player.magic - magic
		target:playSound(95)
		target:sendAnimation(108)
		target:sendStatus()
		player:sendStatus()
	end,
	requirements = function(player)
		local level = 12
		local items = {Item("snake_meat").id, Item("wine").id, 0}
		local itemAmounts = {10, 1, 100}
		local description = "Removes poison from target"
		return level, items, itemAmounts, description
	end
}
