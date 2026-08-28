cleanse_alignment = {
	use = async(function(player)
		local item = player:getInventoryItem(player.invSlot)

		local t = {graphic = item.icon, color = 0}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		local alignments = {"Natural", "Kwi-Sin", "Ming-Ken", "Ohaeng"}
		local currentAlignment = alignments[player.alignment + 1]

		local choice = player:menuSeq(
			"Ramuan ini memungkinkanmu mengganti keberpihakanmu ke yang baru maupun melepasnya tanpa denda vita/mana yang biasanya berlaku.\nKeberpihakan sekarang: " .. currentAlignment,
			alignments,
			{}
		)

		if choice - 1 == player.alignment then
			player:dialogSeq(
				{
					t,
					"Memakai ramuan ini untuk berpihak pada yang sudah kau anut hanya sia-sia."
				},
				0
			)
			return
		end

		local confirm = player:menuSeq(
			"Kau yakin ingin berpihak pada " .. alignments[choice] .. "?",
			{"Ya, ubah keberpihakanku.", "Tidak, lupakan saja."},
			{}
		)

		if player:hasItem("cleanse_alignment", 1) ~= true then
			return
		end

		if confirm == 1 then
			player:removeItem("cleanse_alignment", 1, 6)
			player:swapAlignment(choice - 1)

			if choice == 1 then
				player:dialogSeq(
					{t, "Kau dikembalikan ke keberpihakan alami"},
					0
				)
			else
				player:dialogSeq(
					{t, "Kini kau berpihak pada " .. alignments[choice]},
					0
				)
			end
		elseif confirm == 2 then
			return
		end
	end)
}
