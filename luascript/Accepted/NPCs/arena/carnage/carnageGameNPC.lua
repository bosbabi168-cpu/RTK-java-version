CarnageGameNpc = {
	click = async(function(player, npc)
		local opts = {"Kirim aku kembali"}

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			opts
		)

		if choice == "Kirim aku kembali" then
			if player.state == 1 then
				--dead

				player.state = 0
				player.disguise = 0
				player.speed = 90
				player:calcStat()
				player.health = player.maxHealth
				player.magic = player.maxMagic
				player:flushDuration(1)
				player:updateState()

				if (player.gfxDye == 63) then
					player:warp(3010, 4, 20)
				elseif (player.gfxDye == 60) then
					player:warp(3010, 20, 20)
				elseif (player.gfxDye == 61) then
					player:warp(3010, 12, 20)
				elseif (player.gfxDye == 66) then
					player:warp(3010, 28, 20)
				end
			else
				local confirm = player:menuSeq(
					"Kau meninggalkan regumu! Kau yakin ingin pergi?",
					{"Ya", "Tidak"},
					{}
				)

				if confirm == 1 then
					player.state = 0
					player.disguise = 0
					player.speed = 90
					player:calcStat()
					player.health = player.maxHealth
					player.magic = player.maxMagic
					player:flushDuration(1)
					player:updateState()

					if (player.gfxDye == 63) then
						player:warp(3010, 4, 20)
					elseif (player.gfxDye == 60) then
						player:warp(3010, 20, 20)
					elseif (player.gfxDye == 61) then
						player:warp(3010, 12, 20)
					elseif (player.gfxDye == 66) then
						player:warp(3010, 28, 20)
					end
				end
			end
		end
	end),

	buyItems = function(npc)
		local buyItems = {"bears_liver", "antler", "herb_pipe"}

		return buyItems
	end,

	sellItems = function(npc)
		local buyItems = CarnageHallShopNpc.buyItems(npc)
		return buyItems
	end
}
