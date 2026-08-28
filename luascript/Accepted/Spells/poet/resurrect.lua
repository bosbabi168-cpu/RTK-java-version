resurrect_poet = {
	cast = function(player, target)
		local magicCost = 3000

		if not player:canCast(1, 1, 0) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Kehendakmu terlalu lemah.")
			return
		end

		if player:canPK(target) then
			return
		end

		player:sendAction(6, 20)
		target:sendAnimation(11)
		player.magic = player.magic - magicCost
		player:sendMinitext("Kau merapal Resurrect.")
		player:setAether("resurrect_poet", 8000)
		player:sendStatus()

		if target.state == 1 then
			target.state = 0
			target.health = target.maxHealth
			target:sendMinitext(player.name .. " merapal Resurrect padamu.")
			target:sendStatus()
			target:updateState()
		end
	end,

	requirements = function(player)
		local level = 99
		local items = {
			Item("acorn").id,
			Item("dark_amber").id,
			Item("red_potion").id,
			0
		}
		local itemAmounts = {200, 5, 2, 10000}
		local description = "Brings a dead player back to life at full health."
		return level, items, itemAmounts, description
	end
}

return_spirit_poet = {
	cast = function(player, target)
		local magicCost = 3000

		if not player:canCast(1, 1, 0) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Kehendakmu terlalu lemah.")
			return
		end

		if player:canPK(target) then
			return
		end

		player:sendAction(6, 20)
		target:sendAnimation(96)
		player.magic = player.magic - magicCost
		player:sendMinitext("Kau merapal Return spirit.")
		player:setAether("return_spirit_poet", 8000)
		player:sendStatus()

		if target.state == 1 then
			target.state = 0
			target.health = target.maxHealth
			target:sendMinitext(player.name .. " merapal Return spirit padamu.")
			target:sendStatus()
			target:updateState()
		end
	end,

	requirements = function(player)
		local level = 99
		local items = {
			Item("acorn").id,
			Item("dark_amber").id,
			Item("red_potion").id,
			0
		}
		local itemAmounts = {200, 5, 2, 10000}
		local description = "Brings a dead player back to life at full health."
		return level, items, itemAmounts, description
	end
}

mingken_blessing_poet = {
	cast = function(player, target)
		local magicCost = 3000

		if not player:canCast(1, 1, 0) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Kehendakmu terlalu lemah.")
			return
		end

		if player:canPK(target) then
			return
		end

		player:sendAction(6, 20)
		target:sendAnimation(116)
		player.magic = player.magic - magicCost
		player:sendMinitext("Kau merapal Mingken blessing.")
		player:setAether("mingken_blessing_poet", 8000)
		player:sendStatus()

		if target.state == 1 then
			target.state = 0
			target.health = target.maxHealth
			target:sendMinitext(player.name .. " merapal Mingken blessing padamu.")
			target:sendStatus()
			target:updateState()
		end
	end,

	requirements = function(player)
		local level = 99
		local items = {
			Item("acorn").id,
			Item("dark_amber").id,
			Item("red_potion").id,
			0
		}
		local itemAmounts = {200, 5, 2, 10000}
		local description = "Brings a dead player back to life at full health."
		return level, items, itemAmounts, description
	end
}

death_undone_poet = {
	cast = function(player, target)
		local magicCost = 3000

		if not player:canCast(1, 1, 0) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Kehendakmu terlalu lemah.")
			return
		end

		if player:canPK(target) then
			return
		end

		player:sendAction(6, 20)
		target:sendAnimation(92)
		player.magic = player.magic - magicCost
		player:sendMinitext("Kau merapal Death undone.")
		player:setAether("death_undone_poet", 8000)
		player:sendStatus()

		if target.state == 1 then
			target.state = 0
			target.health = target.maxHealth
			target:sendMinitext(player.name .. " merapal Death undone padamu.")
			target:sendStatus()
			target:updateState()
		end
	end,

	requirements = function(player)
		local level = 99
		local items = {
			Item("acorn").id,
			Item("dark_amber").id,
			Item("red_potion").id,
			0
		}
		local itemAmounts = {200, 5, 2, 10000}
		local description = "Brings a dead player back to life at full health."
		return level, items, itemAmounts, description
	end
}
